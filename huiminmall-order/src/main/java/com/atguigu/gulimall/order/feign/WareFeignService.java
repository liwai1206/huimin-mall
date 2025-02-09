package com.atguigu.gulimall.order.feign;


import com.atguigu.common.utils.R;
import com.atguigu.gulimall.order.vo.WareSkuLockVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient("gulimall-ware")
public interface WareFeignService {
    /**
     * 查询sku是否有库存
     * @return
     */
    @PostMapping(value = "/ware/waresku/hasStock")
    public R getSkuHasStock(@RequestBody List<Long> skuIds);

    /**
     * 查询运费和收货地址信息
     * @param addrId
     * @return
     */
    @GetMapping("/ware/wareinfo/fare")
    public R getFare(@RequestParam("addrId") Long addrId);

    /**
     * 锁定库存
     * @param vo
     * @return
     */
    @PostMapping("/ware/waresku/lock/order")
    public R orderLockStock(@RequestBody WareSkuLockVo vo);
}
