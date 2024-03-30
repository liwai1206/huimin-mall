package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.gulimall.product.entity.AttrGroupEntity;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.CategoryDao;
import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import org.springframework.util.StringUtils;


@Service("categoryService")
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, CategoryEntity> implements CategoryService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<CategoryEntity> page = this.page(
                new Query<CategoryEntity>().getPage(params),
                new QueryWrapper<CategoryEntity>()
        );

        return new PageUtils(page);
    }


    //查询所有分类，以树形结构返回
    @Override
    public List<CategoryEntity> listTree() {

        // 1. 查询到所有的categoryentities
        List<CategoryEntity> categoryEntities = baseMapper.selectList(null);

        // 获取所有根目录
        List<CategoryEntity> rootCategoryEntities = categoryEntities.stream().filter(categoryEntity -> {
            // 获取根目录
            return categoryEntity.getParentCid() == 0;
        }).map(categoryEntity -> {
            categoryEntity.setChildren(getChildren(categoryEntity, categoryEntities));
            return categoryEntity;
        }).sorted((item1, item2) -> {
            return (item1.getSort() == null ? 0 : item1.getSort()) - (item2.getSort() == null ? 0 : item2.getSort());
        }).collect(Collectors.toList());

        return rootCategoryEntities;
    }

    @Override
    public Long[] getCatelogPath(Long catelogId) {

        List<Long> path = new ArrayList<>();

        this.findPath(catelogId, path);

        Collections.reverse( path );

        return path.toArray(new Long[path.size()]);
    }

    /**
     * 查询所有一级分类
     * @return
     */
    @Cacheable(value = "category", key = "#root.method.name", sync = true)
    @Override
    public List<CategoryEntity> getLevel1Categorys() {
        List<CategoryEntity> categoryEntities = baseMapper.selectList(new QueryWrapper<CategoryEntity>().eq("cat_level", 1));
        return categoryEntities;
    }


    /**
     * 封装所有二级分类, 整合cache，并使用redis作为缓存
     * @return
     */
    @Cacheable(value = "category", key = "#root.methodName", sync = true)
    public Map<String, List<Catelog2Vo>> getCategoryCache() {
        // 首先查出所有的category
        List<CategoryEntity> categoryEntityList = baseMapper.selectList(null);

        // 查询一级分类
        List<CategoryEntity> level1Categorys = getCategoryEntities(categoryEntityList, 0l);

        Map<String, List<Catelog2Vo>> listMap = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            // 查出一级分类下的所有二级分类
            List<CategoryEntity> categoryEntities = getCategoryEntities(categoryEntityList, v.getCatId());

            List<Catelog2Vo> catelog2VoList = categoryEntities.stream().map(l2 -> {
                // 封装属性
                Catelog2Vo catelog2Vo = new Catelog2Vo();
                catelog2Vo.setCatalog1Id(v.getCatId().toString());
                catelog2Vo.setId(l2.getCatId().toString());
                catelog2Vo.setName(l2.getName());

                // 查出二级分类下的所有三级分类
                List<CategoryEntity> categoryEntities1 = getCategoryEntities(categoryEntityList, l2.getCatId());
                List<Catelog2Vo.Catelog3Vo> collect = categoryEntities1.stream().map(l3 -> {
                    Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo();
                    catelog3Vo.setId(l3.getCatId().toString());
                    catelog3Vo.setName(l3.getName());
                    catelog3Vo.setCatalog2Id(l2.getCatId().toString());
                    return catelog3Vo;
                }).collect(Collectors.toList());

                catelog2Vo.setCatalog3List(collect);
                return catelog2Vo;
            }).collect(Collectors.toList());

            return catelog2VoList;
        }));
        return listMap;
    }


    /**
     * 封装所有二级分类, redis两次缓存
     * @return
     */
    @Override
    public Map<String, List<Catelog2Vo>> getCatalogJson() {

        // 一次缓存
        String catalogJson = redisTemplate.opsForValue().get("catalogJson");
        if (StringUtils.isEmpty( catalogJson )){
            

            synchronized ( this ){
                // 二次缓存
                catalogJson = redisTemplate.opsForValue().get("catalogJson");
                if ( !StringUtils.isEmpty( catalogJson )){
                    Map<String, List<Catelog2Vo>> map = JSON.parseObject(catalogJson, new TypeReference<Map<String, List<Catelog2Vo>>>() {
                    });
                    return map;
                }

                // 查询数据库
                Map<String, List<Catelog2Vo>> listMap = this.getCategoryListMap();
                // 然后添加到redis中
                String s = JSON.toJSONString(listMap);
                redisTemplate.opsForValue().set("catalogJson", s);

                return listMap;
            }
            

        }

        // 否则，说明redis里面已经存在
        Map<String, List<Catelog2Vo>> map = JSON.parseObject(catalogJson, new TypeReference<Map<String, List<Catelog2Vo>>>() {});
        return map;

    }

    public Map<String, List<Catelog2Vo>> getCategoryListMap() {
        // 首先查出所有的category
        List<CategoryEntity> categoryEntityList = baseMapper.selectList(null);

        // 查询一级分类
        List<CategoryEntity> level1Categorys = getCategoryEntities(categoryEntityList, 0l);

        Map<String, List<Catelog2Vo>> listMap = level1Categorys.stream().collect(Collectors.toMap(k -> k.getCatId().toString(), v -> {
            // 查出一级分类下的所有二级分类
            List<CategoryEntity> categoryEntities = getCategoryEntities(categoryEntityList, v.getCatId());

            List<Catelog2Vo> catelog2VoList = categoryEntities.stream().map(l2 -> {
                // 封装属性
                Catelog2Vo catelog2Vo = new Catelog2Vo();
                catelog2Vo.setCatalog1Id(v.getCatId().toString());
                catelog2Vo.setId(l2.getCatId().toString());
                catelog2Vo.setName(l2.getName());

                // 查出二级分类下的所有三级分类
                List<CategoryEntity> categoryEntities1 = getCategoryEntities(categoryEntityList, l2.getCatId());
                List<Catelog2Vo.Catelog3Vo> collect = categoryEntities1.stream().map(l3 -> {
                    Catelog2Vo.Catelog3Vo catelog3Vo = new Catelog2Vo.Catelog3Vo();
                    catelog3Vo.setId(l3.getCatId().toString());
                    catelog3Vo.setName(l3.getName());
                    catelog3Vo.setCatalog2Id(l2.getCatId().toString());
                    return catelog3Vo;
                }).collect(Collectors.toList());

                catelog2Vo.setCatalog3List(collect);
                return catelog2Vo;
            }).collect(Collectors.toList());

            return catelog2VoList;
        }));
        return listMap;
    }


    // 在categoryEntityList中查询所有id=parentId的集合
    private List<CategoryEntity> getCategoryEntities(List<CategoryEntity> categoryEntityList, Long parentId) {
        List<CategoryEntity> level1Categorys = categoryEntityList.stream().filter(item -> {
            return item.getParentCid() == parentId;
        }).collect(Collectors.toList());
        return level1Categorys;
    }

    private void findPath(Long catelogId, List<Long> path) {

        path.add( catelogId );

        CategoryEntity categoryEntity = baseMapper.selectById(catelogId);
        if ( categoryEntity.getParentCid() != 0 ){
            this.findPath( categoryEntity.getParentCid(), path);
        }
    }


    /**
     * 在所有的category中给root递归封装children
     * @param root
     * @param all
     * @return
     */
    private List<CategoryEntity> getChildren(CategoryEntity root, List<CategoryEntity> all) {

        List<CategoryEntity> entityList = all.stream().filter(categoryEntity -> {
            return categoryEntity.getParentCid() == root.getCatId();
        }).map(categoryEntity -> {
            categoryEntity.setChildren(getChildren(categoryEntity, all));
            return categoryEntity;
        }).sorted((item1, item2) -> {
            return (item1.getSort() == null ? 0 : item1.getSort()) - (item2.getSort() == null ? 0 : item2.getSort());
        }).collect(Collectors.toList());

        return entityList;
    }

}