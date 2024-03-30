package com.atguigu.gulimall.seckill.service.impl;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.AuthLoginConstant;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVo;
import com.atguigu.gulimall.seckill.feign.CouponFeignService;
import com.atguigu.gulimall.seckill.feign.ProductFeignService;
import com.atguigu.gulimall.seckill.intercepter.SeckillLoginIntercepter;
import com.atguigu.gulimall.seckill.service.SeckillService;
import com.atguigu.gulimall.seckill.to.SeckillSkuRedisTo;
import com.atguigu.gulimall.seckill.vo.SeckillSessionWithSkusVo;
import com.atguigu.gulimall.seckill.vo.SkuInfoVo;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RSemaphore;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpSession;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    @Autowired
    private CouponFeignService couponFeignService;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private final String SESSION__CACHE_PREFIX = "seckill:sessions:";

    private final String SECKILL_CHARE_PREFIX = "seckill:skus";

    private final String SKU_STOCK_SEMAPHORE = "seckill:stock:";    //+商品随机码


    @Override
    public void uploadSeckillSkuLatest3Days() {
        // 扫描最近三天的商品需要参加秒杀的活动
        R lates3DaySession = couponFeignService.getLates3DaySession();
        if ( lates3DaySession.getCode() == 0 ){
            // 上架商品
            List<SeckillSessionWithSkusVo> sessionData = lates3DaySession.getData(new TypeReference<List<SeckillSessionWithSkusVo>>() {
            });

            // 缓存到Redis
            // 1.缓存活动信息
            saveSessionInfos( sessionData );

            // 2.缓存活动的关联商品信息
            saveSessionSkuInfo( sessionData );
        }
    }


    /**
     * 获取当前可以参加秒杀的商品信息
     * @return
     */
    @Override
    public List<SeckillSkuRedisTo> getCurrentSeckillSkus() {
        // 1.确定当前属于哪个秒杀场次
        long currentTime = System.currentTimeMillis();

        // 从redis中查询到所有key以seckill:session开头的数据
        Set<String> keys = redisTemplate.keys(SESSION__CACHE_PREFIX + "*");
        for (String key : keys) {
            //seckill:sessions:1594396764000_1594453242000
            String replace = key.replace(SESSION__CACHE_PREFIX, "");
            String[] s = replace.split("_");
            // 开始时间
            long startTime = Long.parseLong(s[0]);
            // 结束时间
            long endTime = Long.parseLong(s[1]);

            // 判断是否是当前秒杀场次
            if ( currentTime >= startTime && currentTime <= endTime ){
                // 2.获取这个秒杀场次需要的所有商品信息
                List<String> range = redisTemplate.opsForList().range(key, 0, -1);
                BoundHashOperations<String, String, String> hasOps = redisTemplate.boundHashOps(SECKILL_CHARE_PREFIX);

                assert  range != null;

                List<String> listValue = hasOps.multiGet(range);
                if ( listValue != null && listValue.size() >= 0 ){
                    List<SeckillSkuRedisTo> collect = listValue.stream().map(item -> {
                        SeckillSkuRedisTo seckillSkuRedisTo = JSON.parseObject(item, SeckillSkuRedisTo.class);
                        return seckillSkuRedisTo;
                    }).collect(Collectors.toList());

                    return  collect;
                }

                break;
            }
        }

        return null;
    }


    /**
     * 根据skuId查询商品是否参加秒杀活动
     * @param skuId
     * @return
     */
    @Override
    public SeckillSkuRedisTo getSkuSeckillInfo(Long skuId) {
        // 1.找到所有需要秒杀的商品的key信息
        BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(SECKILL_CHARE_PREFIX);

        // 拿到所有的key
        Set<String> keys = hashOps.keys();

        if ( keys != null && keys.size() > 0 ){
            for (String key : keys) {
                // 4-45 正则表达式进行匹配
                String reg = "\\d-" + skuId;
                // 如果匹配上了
                if (Pattern.matches(reg, key)) {
                    //从redis中取数据
                    String redisValue = hashOps.get(key);
                    SeckillSkuRedisTo seckillSkuRedisTo = JSON.parseObject(redisValue, SeckillSkuRedisTo.class);

                    // 随机码
                    long currentTimeMillis = System.currentTimeMillis();
                    Long startTime = seckillSkuRedisTo.getStartTime();
                    Long endTime = seckillSkuRedisTo.getEndTime();

                    // 如果当前时间大于等于秒杀活动的开始时间并小于等于活动结束时间
                    if ( currentTimeMillis >= startTime && currentTimeMillis <= endTime ){
                        return seckillSkuRedisTo;
                    }

                    seckillSkuRedisTo.setRandomCode( null );
                    return seckillSkuRedisTo;
                }
            }
        }
        return null;
    }


    /**
     * 商品秒杀
     * @param killId
     * @param key
     * @param num
     * @param session
     * @return
     */
    @Override
    public String kill(String killId, String key, Integer num, HttpSession session) throws InterruptedException {
        long s1 = System.currentTimeMillis();

        // 获取当前用户的信息
        MemberResponseVo user = SeckillLoginIntercepter.threadLocal.get();

        if ( user == null ){
            Object attribute = session.getAttribute(AuthLoginConstant.LOGIN_USER);
            if ( attribute != null ){
                user = JSON.parseObject(JSON.toJSONString( attribute), MemberResponseVo.class);
            }else {
                return null;
            }
        }

        // 1.获取当前秒杀商品的详细信息，从redis中获取
        BoundHashOperations<String, String, String> hashOps = redisTemplate.boundHashOps(SECKILL_CHARE_PREFIX);
        String skuInfoValue = hashOps.get(killId);

        if (StringUtils.isEmpty( skuInfoValue) ){
            return null;
        }

        // 合法性校验
        SeckillSkuRedisTo redisTo = JSON.parseObject(skuInfoValue, SeckillSkuRedisTo.class);
        Long startTime = redisTo.getStartTime();
        Long endTime = redisTo.getEndTime();
        long currentTime = System.currentTimeMillis();

        // 判断是否在秒杀活动时间内
        if ( currentTime >= startTime && currentTime <= endTime ){
            // 2.校验随机码和商品 id
            String randomCode = redisTo.getRandomCode();
            String skuId = redisTo.getPromotionSessionId() + "-" + redisTo.getSkuId();
            if ( randomCode.equals( key ) && skuId.equals(killId) ){
                // 3.校验购物数量是否合理，库存是否充足
                Integer seckillLimit = redisTo.getSeckillLimit();

                // 获取信号量
                String seckillCount = redisTemplate.opsForValue().get(SKU_STOCK_SEMAPHORE + randomCode);
                Integer count = Integer.valueOf(seckillCount);

                // 判断信号量是否大于0，并且买的数量不能超过库存
                if ( count > 0 && num <= seckillLimit && count > num ){
                    // 4.验证当前用户是否已经买过了，如果秒杀成功，就去占位
                    String redisKey = user.getId() + "-" + skuId;
                    // 设置自动过期时间
                    long ttl = endTime - startTime;
                    Boolean aBoolean = redisTemplate.opsForValue().setIfAbsent(redisKey, num.toString(),ttl, TimeUnit.MILLISECONDS);

                    if ( aBoolean ){
                        // 占位成功，说明从来没有买过，信号量-1
                        RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + randomCode);
                        boolean semaphoreCount = semaphore.tryAcquire(num, 100, TimeUnit.MILLISECONDS);

                        // 秒杀成功，快速下单
                        if ( semaphoreCount ){
                            // 创建订单号，将订单信息发送给MQ
                            // 秒杀成功  快速下单  发送消息到MQ   整个操作时间10ms左右
                            String timeId = IdWorker.getTimeId();
                            SeckillOrderTo orderTo = new SeckillOrderTo();
                            orderTo.setOrderSn(timeId);
                            orderTo.setMemberId(user.getId());
                            orderTo.setNum(num);
                            orderTo.setPromotionSessionId(redisTo.getPromotionSessionId());
                            orderTo.setSkuId(redisTo.getSkuId());
                            orderTo.setSeckillPrice(redisTo.getSeckillPrice());

                            rabbitTemplate.convertAndSend("order-event-exchange","order.seckill.order",orderTo);
                            long s2 = System.currentTimeMillis();
                            log.info("耗时：" + (s2-s1));
                            return  timeId;
                        }

                    }
                }
            }
        }
        long s3 = System.currentTimeMillis();
        log.info("耗时..." + (s3 - s1));
        return null;
    }

    /**
     * 缓存秒杀活动所关联的商品信息
     * @param sessions
     */
    private void saveSessionSkuInfo(List<SeckillSessionWithSkusVo> sessions) {

        if ( sessions == null || sessions.size() <= 0 ){
            return;
        }

        sessions.stream().forEach( session -> {
            // 准备hash操作
            BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(SECKILL_CHARE_PREFIX);
            session.getRelationSkus().stream().forEach( seckillSkuVo -> {
                // 生成随机码
                String token = UUID.randomUUID().toString().replace("-", "");
                String redisKey = seckillSkuVo.getPromotionSessionId().toString() + "-" + seckillSkuVo.getSkuId().toString();

                if ( !operations.hasKey( redisKey) ){
                    // 缓存商品信息
                    SeckillSkuRedisTo seckillSkuRedisTo = new SeckillSkuRedisTo();
                    Long skuId = seckillSkuVo.getSkuId();
                    // 1.查询sku的基本信息，调用远程服务
                    R info = productFeignService.info(skuId);
                    if ( info.getCode() == 0 ){
                        SkuInfoVo skuInfo = info.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                        });
                        seckillSkuRedisTo.setSkuInfo( skuInfo );
                    }

                    // 2.sku的秒杀信息
                    BeanUtils.copyProperties( seckillSkuVo, seckillSkuRedisTo);

                    // 3.设置当前商品的秒杀时间信息
                    seckillSkuRedisTo.setStartTime( session.getStartTime().getTime());
                    seckillSkuRedisTo.setEndTime( session.getEndTime().getTime());

                    // 4.设置商品的随机码（防止恶意攻击）
                    seckillSkuRedisTo.setRandomCode( token );

                    // 序列化json格式存入redis中
                    String seckillValue = JSON.toJSONString(seckillSkuRedisTo);
                    operations.put(seckillSkuVo.getPromotionSessionId().toString()+"-"+seckillSkuVo.getSkuId().toString(), seckillValue);

                    // 如果当前这个场次的商品库存信息已经上架了，就无需再次上架
                    // 5.实验库存作为分布式Redisson信号量（用于限流）
                    RSemaphore semaphore = redissonClient.getSemaphore(SKU_STOCK_SEMAPHORE + token);

                    // 商品可以秒杀的数量作为信号量
                    semaphore.trySetPermits(seckillSkuVo.getSeckillCount());
                }
            });
        });
    }

    /**
     * 缓存秒杀活动信息
     * @param sessions
     */
    private void saveSessionInfos(List<SeckillSessionWithSkusVo> sessions) {

        if ( sessions == null || sessions.size() <= 0 ){
            return;
        }

        sessions.stream().forEach( session -> {
            // 获取活动的开始和结束时间的时间戳
            long startTime = session.getStartTime().getTime();
            long endTime = session.getEndTime().getTime();

            // 存到redis中的key
            String key = SESSION__CACHE_PREFIX + startTime + "_" +endTime;
            Boolean hasKey = redisTemplate.hasKey(key);
            if ( !hasKey){
                // 获取到活动中所有商品的skuId，并进行缓存
                List<String> skuIds = session.getRelationSkus().stream().map(item -> item.getPromotionSessionId() + "-" + item.getSkuId().toString()).collect(Collectors.toList());
                redisTemplate.opsForList().leftPushAll( key, skuIds );
            }
        });
    }
}
