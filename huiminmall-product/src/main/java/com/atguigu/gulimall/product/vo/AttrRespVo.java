package com.atguigu.gulimall.product.vo;

import com.atguigu.gulimall.product.entity.AttrEntity;
import lombok.Data;


@Data
public class AttrRespVo extends AttrEntity {

    private String catelogName;
    private String groupName;

    private Long attrGroupId;
    private Long[] catelogPath;
    private Long valueType;

}
