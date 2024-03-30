package com.atguigu.gulimall.coupon.service.impl;

import com.atguigu.gulimall.coupon.entity.SeckillSkuRelationEntity;
import com.atguigu.gulimall.coupon.service.SeckillSkuRelationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.coupon.dao.SeckillSessionDao;
import com.atguigu.gulimall.coupon.entity.SeckillSessionEntity;
import com.atguigu.gulimall.coupon.service.SeckillSessionService;
import org.springframework.util.StringUtils;


@Service("seckillSessionService")
public class SeckillSessionServiceImpl extends ServiceImpl<SeckillSessionDao, SeckillSessionEntity> implements SeckillSessionService {

    @Autowired
    private SeckillSkuRelationService seckillSkuRelationService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        QueryWrapper<SeckillSessionEntity> queryWrapper = new QueryWrapper<>();

        String key = (String) params.get("key");
        if ( !StringUtils.isEmpty(key)){
            queryWrapper.eq("id", key);
        }

        IPage<SeckillSessionEntity> page = this.page(
                new Query<SeckillSessionEntity>().getPage(params),
                queryWrapper
        );

        return new PageUtils(page);
    }


    /**
     * 查询最近三天参与秒杀活动的商品
     * @return
     */
    @Override
    public List<SeckillSessionEntity> getLates3DaySession() {
        List<SeckillSessionEntity> list = baseMapper.selectList(new QueryWrapper<SeckillSessionEntity>().between("start_time", startTime(), endTime()));

        if ( list != null && list.size() >0){
            List<SeckillSessionEntity> collect = list.stream().map(session -> {
                Long id = session.getId();
                // 查出sms_seckill_sku_relation表中关联的skuId
                List<SeckillSkuRelationEntity> relationSkus = seckillSkuRelationService.list(new QueryWrapper<SeckillSkuRelationEntity>().eq("promotion_session_id", id));
                session.setRelationSkus(relationSkus);
                return session;
            }).collect(Collectors.toList());

            return collect;
        }

        return null;
    }

    private String endTime() {
        LocalDate now = LocalDate.now();
        LocalDate plusDays = now.plusDays(2);
        LocalTime max = LocalTime.MAX;

        LocalDateTime of = LocalDateTime.of(plusDays, max);
        String endTime = of.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return endTime;
    }

    private String startTime() {
        LocalDate now = LocalDate.now();
        LocalTime min = LocalTime.MIN;

        LocalDateTime of = LocalDateTime.of(now, min);
        String startTime = of.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        return startTime;
    }

}