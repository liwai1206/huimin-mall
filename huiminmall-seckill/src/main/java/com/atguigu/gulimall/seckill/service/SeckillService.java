package com.atguigu.gulimall.seckill.service;

import com.atguigu.gulimall.seckill.to.SeckillSkuRedisTo;

import javax.servlet.http.HttpSession;
import java.util.List;

public interface SeckillService {

    void uploadSeckillSkuLatest3Days();

    List<SeckillSkuRedisTo> getCurrentSeckillSkus();

    SeckillSkuRedisTo getSkuSeckillInfo(Long skuId);

    String kill(String killId, String key, Integer num, HttpSession session) throws InterruptedException;
}
