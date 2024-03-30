package com.atguigu.gulimall.product.app;

import java.util.Arrays;
import java.util.List;

import com.atguigu.gulimall.product.service.CategoryBrandRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.atguigu.gulimall.product.entity.CategoryEntity;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.common.utils.R;



/**
 * ??Ʒ???????
 *
 * @author 22wli
 * @email 22wli@gmail.com
 * @date 2023-10-14 13:42:17
 */
@RestController
@RequestMapping("product/category")
public class CategoryController {
    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CategoryBrandRelationService categoryBrandRelationService;

    /**
     * 查询列表，以树形结构返回
     */
    @RequestMapping("/list/tree")
    //////@RequiresPermissions("product:category:list")
    public R list(){
         List<CategoryEntity> categoryEntities = categoryService.listTree();

        return R.ok().put("data", categoryEntities);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{catId}")
    //////@RequiresPermissions("product:category:info")
    public R info(@PathVariable("catId") Long catId){
		CategoryEntity category = categoryService.getById(catId);

        return R.ok().put("data", category);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    //////@RequiresPermissions("product:category:save")
    public R save(@RequestBody CategoryEntity category){
		categoryService.save(category);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    //////@RequiresPermissions("product:category:update")
    public R update(@RequestBody CategoryEntity category){
		categoryService.updateById(category);


		// 级联更新类别品牌关系表中的内容
		if ( !StringUtils.isEmpty( category.getName())){
            categoryBrandRelationService.updateCategoryName( category.getCatId(), category.getName() );
        }

        return R.ok();
    }

    @RequestMapping("/update/sort")
    //////@RequiresPermissions("product:category:update")
    public R updateSort(@RequestBody CategoryEntity[] category){

        categoryService.updateBatchById( Arrays.asList(category) );

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    //////@RequiresPermissions("product:category:delete")
    public R delete(@RequestBody Long[] catIds){
		categoryService.removeByIds(Arrays.asList(catIds));

        return R.ok();
    }

}
