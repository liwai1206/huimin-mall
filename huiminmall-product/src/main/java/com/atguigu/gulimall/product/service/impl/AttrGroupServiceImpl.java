package com.atguigu.gulimall.product.service.impl;

import com.atguigu.gulimall.product.entity.AttrAttrgroupRelationEntity;
import com.atguigu.gulimall.product.entity.AttrEntity;
import com.atguigu.gulimall.product.service.AttrAttrgroupRelationService;
import com.atguigu.gulimall.product.service.AttrService;
import com.atguigu.gulimall.product.vo.AttrGroupWithAttrVo;
import com.atguigu.gulimall.product.vo.SpuItemAttrGroupVo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.AttrGroupDao;
import com.atguigu.gulimall.product.entity.AttrGroupEntity;
import com.atguigu.gulimall.product.service.AttrGroupService;
import org.springframework.util.StringUtils;


@Service("attrGroupService")
public class AttrGroupServiceImpl extends ServiceImpl<AttrGroupDao, AttrGroupEntity> implements AttrGroupService {

    @Autowired
    private AttrAttrgroupRelationService relationService;

    @Autowired
    private AttrService attrService;

    @Autowired
    private AttrGroupDao attrGroupDao;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<AttrGroupEntity> page = this.page(
                new Query<AttrGroupEntity>().getPage(params),
                new QueryWrapper<AttrGroupEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params, Long catelogId) {
        QueryWrapper<AttrGroupEntity> wrapper = new QueryWrapper<>();
        String key = (String) params.get("key");
        if ( !StringUtils.isEmpty( key )){
            wrapper.and( (obj)-> {
                obj.eq("attr_group_id", key).or().like("attr_group_name",key);
            });
        }

        if ( catelogId == 0 ){
            // 没有给定要查询的catelogId，进行默认查询
            IPage<AttrGroupEntity> page = this.page(new Query<AttrGroupEntity>().getPage(params),wrapper);

            return new PageUtils(page);
        }else {
            wrapper.eq("catelog_id", catelogId);

            IPage<AttrGroupEntity> page = this.page(new Query<AttrGroupEntity>().getPage(params), wrapper);

            return new PageUtils(page);
        }
    }


    // 获取属性分组的关联的所有属性
    @Override
    public List<AttrEntity> selectAttrByGroupId(Long attrgroupId) {

        // 1.根据attrgroupId查询所有attrId
        List<Long> attrIds =  relationService.getAttrIdsByGroupId(attrgroupId);

        if ( attrIds == null || attrIds.size() == 0){
            return null;
        }

        // 2.根据attrId，查询所有attr
        List<AttrEntity> attrEntityList =  attrService.getBatchByIds( attrIds );

        return attrEntityList;
    }



    @Override
    public void deleteByAttrIdAndGroupId(List<AttrAttrgroupRelationEntity> params) {
        attrGroupDao.deleteByAttrIdAndGroupId(params);
    }


    /**
     * 获取分类下所有分组&关联属性
     * @param catelogId
     * @return
     */
    @Override
    public List<AttrGroupWithAttrVo> getAttrGroupWithAttrByCatelogId(Long catelogId) {

        // 1.获取分类下的分组
        List<AttrGroupEntity> groupEntityList = this.list(new QueryWrapper<AttrGroupEntity>().eq("catelog_id", catelogId));

        // 2.获取分组下的所有属性
        List<AttrGroupWithAttrVo> collect = groupEntityList.stream().map(item -> {

            AttrGroupWithAttrVo attrGroupWithAttrVo = new AttrGroupWithAttrVo();
            BeanUtils.copyProperties(item, attrGroupWithAttrVo);

            // 查询分组下的所有属性
            Long attrGroupId = item.getAttrGroupId();
            List<Long> attrIds = relationService.getAttrIdsByGroupId(attrGroupId);
            if ( attrIds != null && attrIds.size() > 0 ){
                List<AttrEntity> attrList = attrService.getAllAttrEntityByAttrIds(attrIds);

                attrGroupWithAttrVo.setAttrs(attrList);
            }

            return attrGroupWithAttrVo;

        }).collect(Collectors.toList());


        return collect;
    }

    @Override
    public List<SpuItemAttrGroupVo> getAttrGroupWithAttrsBySpuId(Long spuId, Long catalogId) {

        //1、查出当前spu对应的所有属性的分组信息以及当前分组下的所有属性对应的值
        List<SpuItemAttrGroupVo> vos =  baseMapper.getAttrGroupWithAttrsBySpuId( spuId, catalogId );
        return vos;
    }


}