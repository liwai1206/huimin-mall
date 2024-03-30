package com.atguigu.gulimall.product.feign;


import com.atguigu.common.to.SkuReductionTo;
import com.atguigu.common.to.SpuBoundTo;
import com.atguigu.common.utils.R;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("gulimall-coupon")
public interface CouponFeignService {

    /**
     * 保存
     */
    @PostMapping("/coupon/spubounds/save")
    ////@RequiresPermissions("coupon:spubounds:save")
    R saveSpuBounds(@RequestBody SpuBoundTo spuBounds);

    @PostMapping("/coupon/skufullreduction/saveinfo")
    R saveSkuReduction(@RequestBody SkuReductionTo skuReductionTo);
}
