package com.atguigu.gulimall.product.service;

import com.atguigu.gulimall.product.vo.AttrVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.product.entity.AttrAttrgroupRelationEntity;

import java.util.List;
import java.util.Map;

/**
 * ????&???Է???????
 *
 * @author 22wli
 * @email 22wli@gmail.com
 * @date 2023-10-14 13:42:17
 */
public interface AttrAttrgroupRelationService extends IService<AttrAttrgroupRelationEntity> {

    PageUtils queryPage(Map<String, Object> params);

    List<Long> getAttrIdsByGroupId(Long attrgroupId);

    void saveBatch(List<AttrVo> attrVoList);
}

