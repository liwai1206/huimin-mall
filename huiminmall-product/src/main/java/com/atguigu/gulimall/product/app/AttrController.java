package com.atguigu.gulimall.product.app;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.atguigu.gulimall.product.entity.ProductAttrValueEntity;
import com.atguigu.gulimall.product.service.ProductAttrValueService;
import com.atguigu.gulimall.product.vo.AttrRespVo;
import com.atguigu.gulimall.product.vo.AttrVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.atguigu.gulimall.product.service.AttrService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.R;



/**
 * ??Ʒ?
 *
 * @author 22wli
 * @email 22wli@gmail.com
 * @date 2023-10-14 13:42:17
 */
@RestController
@RequestMapping("product/attr")
public class AttrController {
    @Autowired
    private AttrService attrService;

    @Autowired
    private ProductAttrValueService productAttrValueService;


    /**
     * 获取spu规格
     * @param spuId
     * @return
     */
    @GetMapping("/base/listforspu/{spuId}")
    public R listforspu( @PathVariable("spuId" ) Long spuId){
        List<ProductAttrValueEntity> entities = productAttrValueService.listForSpu(spuId);
        return R.ok().put("data", entities);
    }

    /**
     * 列表
     */
    @RequestMapping("/list")
    //////@RequiresPermissions("product:attr:list")
    public R list(@RequestParam Map<String, Object> params){
        PageUtils page = attrService.queryPage(params);

        return R.ok().put("page", page);
    }

    @RequestMapping("/{requestCatelog}/list/{catelogId}")
    //////@RequiresPermissions("product:attr:list")
    public R list(@RequestParam Map<String, Object> params,
                  @PathVariable("catelogId") Long catelogId,
                  @PathVariable("requestCatelog") String requestCatelog){

        PageUtils page = attrService.queryBaseAttrPage(params,catelogId,requestCatelog);

        return R.ok().put("page", page);
    }


    /**
     * 信息
     */
    @RequestMapping("/info/{attrId}")
    //////@RequiresPermissions("product:attr:info")
    public R info(@PathVariable("attrId") Long attrId){
//		AttrEntity attr = attrService.getById(attrId);
        AttrRespVo attrRespVo = attrService.getAttrInfo(attrId);

        return R.ok().put("attr", attrRespVo);
    }

    /**
     * 保存
     */
    @RequestMapping("/save")
    //////@RequiresPermissions("product:attr:save")
    public R save(@RequestBody AttrVo attr){
//		attrService.save(attr);
        attrService.saveAttr(attr);
        return R.ok();
    }

    /**
     * 修改
     */
    @RequestMapping("/update")
    //////@RequiresPermissions("product:attr:update")
    public R update(@RequestBody AttrRespVo attr){
//		attrService.updateById(attr);
        attrService.updateAttr( attr );

        return R.ok();
    }

    @RequestMapping("/update/{spuId}")
    //////@RequiresPermissions("product:attr:update")
    public R updateBySpuId(@RequestBody List<ProductAttrValueEntity> attr,
                           @PathVariable("spuId") Long spuId){
//		attrService.updateById(attr);
        attrService.updateAttrBySpuId( attr,spuId );

        return R.ok();
    }

    /**
     * 删除
     */
    @RequestMapping("/delete")
    //////@RequiresPermissions("product:attr:delete")
    public R delete(@RequestBody Long[] attrIds){
		attrService.removeByIds(Arrays.asList(attrIds));

        return R.ok();
    }

}
