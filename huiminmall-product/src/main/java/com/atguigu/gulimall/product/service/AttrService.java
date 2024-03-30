package com.atguigu.gulimall.product.service;

import com.atguigu.gulimall.product.entity.ProductAttrValueEntity;
import com.atguigu.gulimall.product.vo.AttrRespVo;
import com.atguigu.gulimall.product.vo.AttrVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.product.entity.AttrEntity;

import java.util.List;
import java.util.Map;

/**
 * ??Ʒ?
 *
 * @author 22wli
 * @email 22wli@gmail.com
 * @date 2023-10-14 13:42:17
 */
public interface AttrService extends IService<AttrEntity> {

    PageUtils queryBaseAttrPage(Map<String, Object> params, Long catelogId, String requestCatelog);

    void saveAttr(AttrVo attrVo);

    PageUtils queryPage(Map<String, Object> params);

    AttrRespVo getAttrInfo(Long attrId);

    void updateAttr(AttrRespVo attr);

    List<AttrEntity> getBatchByIds(List<Long> attrIds);

    PageUtils getNoRelationAttr(Map<String, Object> params, Long attrgroupId);

    List<AttrEntity> getAllAttrEntityByAttrIds(List<Long> attrIds);

    void updateAttrBySpuId(List<ProductAttrValueEntity> attr, Long spuId);

    List<Long> selectSearchAttrs(List<Long> attrIds);
}

