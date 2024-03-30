package com.atguigu.gulimall.product.vo;


import com.atguigu.gulimall.product.entity.AttrEntity;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.util.List;

@Data
public class AttrGroupWithAttrVo {

    /**
     * ????id
     */
    @TableId
    private Long attrGroupId;
    /**
     * ????
     */
    private String attrGroupName;
    /**
     * ???
     */
    private Integer sort;
    /**
     * ????
     */
    private String descript;
    /**
     * ??ͼ?
     */
    private String icon;
    /**
     * ????????id
     */
    private Long catelogId;

    private List<AttrEntity> attrs;

}
