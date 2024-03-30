package com.atguigu.gulimall.product.service.impl;

import com.atguigu.common.constant.ProductConstant;
import com.atguigu.gulimall.product.dao.AttrAttrgroupRelationDao;
import com.atguigu.gulimall.product.dao.AttrGroupDao;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.AttrRespVo;
import com.atguigu.gulimall.product.vo.AttrVo;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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

import com.atguigu.gulimall.product.dao.AttrDao;
import org.springframework.util.StringUtils;


@Service("attrService")
public class AttrServiceImpl extends ServiceImpl<AttrDao, AttrEntity> implements AttrService {

    @Autowired
    private AttrAttrgroupRelationService attrAttrgroupRelationService;

    @Autowired
    private AttrAttrgroupRelationDao attrAttrgroupRelationDao;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private AttrGroupService attrGroupService;

    @Autowired
    private AttrGroupDao attrGroupDao;

    @Autowired
    private ProductAttrValueService productAttrValueService;

    @Autowired
    private AttrDao attrDao;

    @Override
    public PageUtils queryBaseAttrPage(Map<String, Object> params, Long catelogId, String requestCatelog) {
        QueryWrapper<AttrEntity> wrapper = new QueryWrapper<AttrEntity>()
                .eq("attr_type", "base".equalsIgnoreCase(requestCatelog)?
                        ProductConstant.AttrEunm.ATTR_TYPE_BASE.getCode():ProductConstant.AttrEunm.ATTR_TYPE_SALE.getCode());

        // 封装查询的类别
        if ( catelogId != 0 ){
            wrapper.eq("catelog_id",catelogId);
        }

        // 封装查询的参数
        String key = (String) params.get("key");
        if (!StringUtils.isEmpty( key )){
            wrapper.and(( obj ) -> {
                obj.eq("attr_id", key).or().like("attr_name", key);
            });
        }

        IPage<AttrEntity> page = this.page(
                new Query<AttrEntity>().getPage(params),
                wrapper
        );

        PageUtils pageUtils = new PageUtils(page);

        // 对查询到的Attr实体进行增强
        List<AttrEntity> list = (List<AttrEntity>) pageUtils.getList();
        List<AttrRespVo> respVoList = list.stream().map((attrEntity) -> {
            AttrRespVo attrRespVo = new AttrRespVo();
            BeanUtils.copyProperties(attrEntity, attrRespVo);

            // 设置所属分类名称
            CategoryEntity categoryEntity = categoryService.getById(attrEntity.getCatelogId());
            if (categoryEntity != null) {
                attrRespVo.setCatelogName(categoryEntity.getName());
            }

            // 设置所属分组名称,只有基本属性才有分组
            if ( "base".equalsIgnoreCase( requestCatelog )){
                Long attrId = attrEntity.getAttrId();
                AttrAttrgroupRelationEntity relationEntity = attrAttrgroupRelationService.getOne(new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", attrId));
                if (relationEntity != null) {
                    Long attrGroupId = relationEntity.getAttrGroupId();

                    AttrGroupEntity groupEntity = attrGroupService.getById(attrGroupId);
                    if (groupEntity != null) {
                        attrRespVo.setGroupName(groupEntity.getAttrGroupName());
                    }
                }
            }


            // 封装valueType
            String valueSelect = attrRespVo.getValueSelect();
            if ( valueSelect .contains(";") ){
                attrRespVo.setValueType( 1l );
            }else {
                attrRespVo.setValueType( 0l );
            }

            return attrRespVo;
        }).collect(Collectors.toList());

        pageUtils.setList( respVoList );
        return pageUtils;
    }


    /**
     * 保存规格参数，包括在中间表中添加数据
     * @param attrVo
     */
    @Override
    public void saveAttr(AttrVo attrVo) {
        // 1.先保存基本的规格参数
        AttrEntity attrEntity = new AttrEntity();
        BeanUtils.copyProperties( attrVo,attrEntity);
        this.save( attrEntity );

        // 2.保存中间表数据,只有基本属性才需要保存和组相关的信息
        if ( attrVo.getAttrType() == ProductConstant.AttrEunm.ATTR_TYPE_BASE.getCode()){
            Long attrId = attrEntity.getAttrId();
            Long attrGroupId = attrVo.getAttrGroupId();

            AttrAttrgroupRelationEntity relationEntity = new AttrAttrgroupRelationEntity();
            relationEntity.setAttrId( attrId );
            relationEntity.setAttrGroupId( attrGroupId );
            relationEntity.setAttrSort(0);
            attrAttrgroupRelationService.save( relationEntity );
        }
    }

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<AttrEntity> page = this.page(
                new Query<AttrEntity>().getPage(params),
                new QueryWrapper<>()
        );

        return new PageUtils(page);
    }

    @Override
    public AttrRespVo getAttrInfo(Long attrId) {
        // 1.填充基本属性
        AttrEntity attrEntity = baseMapper.selectById(attrId);
        AttrRespVo attrRespVo = new AttrRespVo();
        BeanUtils.copyProperties( attrEntity, attrRespVo );

        // 2.根据attrId查询attrGroupId
        AttrAttrgroupRelationEntity relationEntity = attrAttrgroupRelationService.getOne(new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", attrId));
        if ( relationEntity != null ){
            attrRespVo.setAttrGroupId( relationEntity.getAttrGroupId() );
            // 顺便可以封装groupName
            AttrGroupEntity groupEntity = attrGroupService.getById(relationEntity.getAttrGroupId());
            if ( groupEntity != null ){
                attrRespVo.setGroupName( groupEntity.getAttrGroupName() );
            }
        }

        // 3.封装分类完整路径
        Long[] catelogPath = categoryService.getCatelogPath(attrEntity.getCatelogId());
        attrRespVo.setCatelogPath( catelogPath );

        // 4.封装valueType
        String valueSelect = attrRespVo.getValueSelect();
        if ( valueSelect .contains(";") ){
            attrRespVo.setValueType( 1l );
        }else {
            attrRespVo.setValueType( 0l );
        }

        return attrRespVo;
    }

    @Override
    public void updateAttr(AttrRespVo attr) {
        // 1.更新基本信息
        AttrEntity attrEntity = new AttrEntity();
        BeanUtils.copyProperties( attr, attrEntity);
        baseMapper.updateById( attrEntity );

        // 2.更新中间表信息
        if ( attr.getAttrType() == ProductConstant.AttrEunm.ATTR_TYPE_BASE.getCode()){
            Long attrGroupId = attr.getAttrGroupId();
            Long attrId = attr.getAttrId();

            AttrAttrgroupRelationEntity attrAttrgroupRelationEntity = new AttrAttrgroupRelationEntity();
            attrAttrgroupRelationEntity.setAttrId( attrId );
            attrAttrgroupRelationEntity.setAttrGroupId( attrGroupId );

            int count = attrAttrgroupRelationService.count(new QueryWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", attrId));
            if ( count > 0 ){
                attrAttrgroupRelationService.update(attrAttrgroupRelationEntity, new UpdateWrapper<AttrAttrgroupRelationEntity>().eq("attr_id", attrId));
            }else {
                attrAttrgroupRelationService.save( attrAttrgroupRelationEntity );
            }
        }

    }

    @Override
    public List<AttrEntity> getBatchByIds(List<Long> attrIds) {
        List<AttrEntity> attrEntityList = baseMapper.selectBatchIds(attrIds);
        return attrEntityList;
    }


    /**
     * 获取当前分组没有被关联的所有属性
     * @param params
     * @param attrgroupId
     * @return
     */
    @Override
    public PageUtils getNoRelationAttr(Map<String, Object> params, Long attrgroupId) {

        // 1.当前分组只能关联自己所属的分类里面的所有属性
        // 获取当前分组所属的分类id
        AttrGroupEntity groupEntity = attrGroupService.getById(attrgroupId);
        Long catelogId = groupEntity.getCatelogId();

        // 2.当前分组只能关联这个分类中别的分组没有引用的属性
        // 2.1 当前分类下的其他分组
        List<AttrGroupEntity> groupEntities = attrGroupDao.selectList(new QueryWrapper<AttrGroupEntity>().eq("catelog_id", catelogId));
        // 获取这些分组的attrGroupId
        List<Long> collect = groupEntities.stream().map(item -> {
            return item.getAttrGroupId();
        }).collect(Collectors.toList());

        // 2.2 查询这些分组关联的属性
        List<AttrAttrgroupRelationEntity> relationEntityList = attrAttrgroupRelationDao.selectList(new QueryWrapper<AttrAttrgroupRelationEntity>().in("attr_group_id", collect));
        List<Long> attrIds = relationEntityList.stream().map(item -> {
            return item.getAttrId();
        }).collect(Collectors.toList());

        // 2.3 从当前分类的所有属性中移除这些属性
        QueryWrapper<AttrEntity> queryWrapper = new QueryWrapper<AttrEntity>()
                .eq("catelog_id",catelogId).eq("attr_type",ProductConstant.AttrEunm.ATTR_TYPE_BASE.getCode());

        if ( attrIds != null && attrIds.size() > 0 ){
            queryWrapper.notIn("attr_id", attrIds);
        }

        // 判断是否有参数需要进行模糊查询
        String key = (String) params.get("key");
        if ( !StringUtils.isEmpty(key)){
            queryWrapper.and( w -> {
                w.eq("attr_id", key).or().like("attr_name", key);
            });
        }

        // 分页查询
        IPage<AttrEntity> page = this.page(new Query<AttrEntity>().getPage(params), queryWrapper);

        return new PageUtils(page);
    }

    @Override
    public List<AttrEntity> getAllAttrEntityByAttrIds(List<Long> attrIds) {

        List<AttrEntity> attrEntityList = baseMapper.selectList(new QueryWrapper<AttrEntity>().in("attr_id", attrIds));

        return attrEntityList;
    }

    @Override
    public void updateAttrBySpuId(List<ProductAttrValueEntity> attr, Long spuId) {
        productAttrValueService.remove( new QueryWrapper<ProductAttrValueEntity>().eq("spu_id", spuId));

        attr.forEach( item -> {
            item.setSpuId( spuId );
        });

        productAttrValueService.saveBatch( attr );
    }

    @Override
    public List<Long> selectSearchAttrs(List<Long> attrIds) {

        List<Long> attrs = attrDao.selectSearchAttrs(attrIds);

        return attrs;
    }

}