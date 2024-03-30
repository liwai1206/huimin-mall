package com.atguigu.gulimall.product.app;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.atguigu.gulimall.product.entity.AttrAttrgroupRelationEntity;
import com.atguigu.gulimall.product.entity.AttrEntity;
import com.atguigu.gulimall.product.service.AttrAttrgroupRelationService;
import com.atguigu.gulimall.product.service.AttrService;
import com.atguigu.gulimall.product.service.CategoryService;
import com.atguigu.gulimall.product.vo.AttrGroupWithAttrVo;
import com.atguigu.gulimall.product.vo.AttrVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.atguigu.gulimall.product.entity.AttrGroupEntity;
import com.atguigu.gulimall.product.service.AttrGroupService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.R;



/**
 * ???Է??
 *
 * @author 22wli
 * @email 22wli@gmail.com
 * @date 2023-10-14 13:42:17
 */
@RestController
@RequestMapping("product/attrgroup")
public class AttrGroupController {
    @Autowired
    private AttrGroupService attrGroupService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private AttrService attrService;

    @Autowired
    private AttrAttrgroupRelationService relationService;

    /**
     * 列表
     */
    @RequestMapping("/list/{catelogId}")
    //////@RequiresPermissions("product:attrgroup:list")
    public R list(@RequestParam Map<String, Object> params,
                  @PathVariable("catelogId") Long catelogId){

        PageUtils page = attrGroupService.queryPage( params, catelogId);

        return R.ok().put("page", page);
    }


    /**
     * 获取属性分组的关联的所有属性
     * @param attrgroupId
     * @return
     */
    @GetMapping("/{attrgroupId}/attr/relation")
    public R attrRelation( @PathVariable("attrgroupId") Long attrgroupId){
        List<AttrEntity> data =  attrGroupService.selectAttrByGroupId(attrgroupId);
        return R.ok().put("data",data);
    }


    /**
     * 获取属性分组没有关联的其他属性
     * @param attrgroupId
     * @param params
     * @return
     */
    @GetMapping("/{attrgroupId}/noattr/relation")
    public R attrNoRelation(@PathVariable("attrgroupId") Long attrgroupId,
                            @RequestParam Map<String, Object> params){

        PageUtils page = attrService.getNoRelationAttr(params, attrgroupId);

        return R.ok().put("page", page);
    }

    ///product/attrgroup/attr/relation
    @PostMapping("/attr/relation")
    public R attrRelation( @RequestBody List<AttrVo> attrVoList){

        relationService.saveBatch(attrVoList);

        return R.ok();
    }


    /**
     * 获取分类下所有分组&关联属性
     * @param catelogId
     * @return
     */
    // /product/attrgroup/{catelogId}/withattr
    @GetMapping("/{catelogId}/withattr")
    public R attrGroupWithAttr( @PathVariable("catelogId") Long catelogId){
        List<AttrGroupWithAttrVo> attrVoList = attrGroupService.getAttrGroupWithAttrByCatelogId(catelogId);
        return R.ok().put("data", attrVoList);
    }

    /**
     * 信息
     */
    @RequestMapping("/info/{attrGroupId}")
    //////@RequiresPermissions("product:attrgroup:info")
    public R info(@PathVariable("attrGroupId") Long attrGroupId){
		AttrGroupEntity attrGroup = attrGroupService.getById(attrGroupId);

		// 通过category查询path
        attrGroup.setCatelogPath(categoryService.getCatelogPath( attrGroup.getCatelogId() ));

        return R.ok().put("attrGroup", attrGroup);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    //////@RequiresPermissions("product:attrgroup:save")
    public R save(@RequestBody AttrGroupEntity attrGroup){
		attrGroupService.save(attrGroup);

        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    //////@RequiresPermissions("product:attrgroup:update")
    public R update(@RequestBody AttrGroupEntity attrGroup){
		attrGroupService.updateById(attrGroup);

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    //////@RequiresPermissions("product:attrgroup:delete")
    public R delete(@RequestBody Long[] attrGroupIds){
		attrGroupService.removeByIds(Arrays.asList(attrGroupIds));

        return R.ok();
    }

    @PostMapping("/attr/relation/delete")
    public R attrRelationDelete( @RequestBody List<AttrAttrgroupRelationEntity> params){

        attrGroupService.deleteByAttrIdAndGroupId(params);
        return R.ok();

    }

}
