package com.atguigu.gulimall.product.service;

import com.atguigu.gulimall.product.entity.AttrGroupEntity;
import com.atguigu.gulimall.product.vo.Catelog2Vo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.product.entity.CategoryEntity;

import java.util.List;
import java.util.Map;

/**
 * ??Ʒ???????
 *
 * @author 22wli
 * @email 22wli@gmail.com
 * @date 2023-10-14 13:42:17
 */
public interface CategoryService extends IService<CategoryEntity> {

    PageUtils queryPage(Map<String, Object> params);


    // 查询所有分类，以树形结构返回
    List<CategoryEntity> listTree();

    // 查询所属分类的路径
    Long[] getCatelogPath(Long catelogId);

    // 查询所有一级分类
    List<CategoryEntity> getLevel1Categorys();

    // 封装所有二级分类
    Map<String, List<Catelog2Vo>> getCatalogJson();

    // 封装所有二级分类
    Map<String, List<Catelog2Vo>> getCategoryCache();
}

