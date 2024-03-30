package com.atguigu.gulimall.seckill.controller;


import com.atguigu.common.utils.R;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SeckillSkuRedisTo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpSession;
import java.util.List;

@Controller
public class SeckillController {

    @Autowired
    private SeckillService seckillService;


    /**
     * 当前时间可以参与秒杀的商品信息
     * @return
     */
    @GetMapping("/getCurrentSeckillSkus")
    @ResponseBody
    public R getCurrentSeckillSkus(){
        // 获取当前可以参加秒杀的商品的信息
        List<SeckillSkuRedisTo> vos =  seckillService.getCurrentSeckillSkus();
        return R.ok().setData(vos);
    }


    @GetMapping("/sku/seckill/{skuId}")
    @ResponseBody
    public R getSkuSeckillInfo(@PathVariable("skuId") Long skuId){
        SeckillSkuRedisTo to = seckillService.getSkuSeckillInfo( skuId );
        return R.ok().setData( to );
    }


    /**
     * 进行秒杀
     * @param killId
     * @param key
     * @param num
     * @return
     */
    @GetMapping("/kill")
    public String seckill(@RequestParam("killId") String killId,
                          @RequestParam("key") String key,
                          @RequestParam("num") Integer num,
                          HttpSession session,
                          Model model) throws InterruptedException {


        String orderSn = null;
        orderSn = seckillService.kill( killId, key, num,session );
        model.addAttribute("orderSn", orderSn);

        return "success";
    }

}
