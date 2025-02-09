package com.atguigu.gulimall.order.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.CartConstant;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.OrderTo;
import com.atguigu.common.to.mq.SeckillOrderTo;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVo;
import com.atguigu.gulimall.order.constant.OrderConstant;
import com.atguigu.gulimall.order.entity.OrderItemEntity;
import com.atguigu.gulimall.order.entity.PaymentInfoEntity;
import com.atguigu.gulimall.order.enume.OrderStatusEnum;
import com.atguigu.gulimall.order.feign.CartFeignService;
import com.atguigu.gulimall.order.feign.MemberFeignService;
import com.atguigu.gulimall.order.feign.ProductFeignService;
import com.atguigu.gulimall.order.feign.WareFeignService;
import com.atguigu.gulimall.order.intercepter.OrderLoginIntercepter;
import com.atguigu.gulimall.order.service.OrderItemService;
import com.atguigu.gulimall.order.service.PaymentInfoService;
import com.atguigu.gulimall.order.to.OrderCreateTo;
import com.atguigu.gulimall.order.to.SpuInfoVo;
import com.atguigu.gulimall.order.vo.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.order.dao.OrderDao;
import com.atguigu.gulimall.order.entity.OrderEntity;
import com.atguigu.gulimall.order.service.OrderService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;


@Service("orderService")
public class OrderServiceImpl extends ServiceImpl<OrderDao, OrderEntity> implements OrderService {

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private OrderItemService orderItemService;

    @Autowired
    private MemberFeignService memberFeignService;

    @Autowired
    private CartFeignService cartFeignService;

    @Autowired
    private WareFeignService wareFeignService;

    @Autowired
    private ThreadPoolExecutor executor;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PaymentInfoService paymentInfoService;

    private ThreadLocal<OrderSubmitVo> orderSubmitVoThreadLocal = new ThreadLocal<>();


    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>()
        );

        return new PageUtils(page);
    }


    /**
     * 查询订单详情
     * @return
     */
    @Override
    public OrderConfirmVo confirmOrder() throws ExecutionException, InterruptedException {
        OrderConfirmVo orderConfirmVo = new OrderConfirmVo();
        MemberResponseVo memberResponseVo = OrderLoginIntercepter.threadLocal.get();

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        CompletableFuture<Void> addressTask = CompletableFuture.runAsync(() -> {
            // 1. 查询用户收货地址列表
            RequestContextHolder.setRequestAttributes(requestAttributes);
            List<MemberAddressVo> memberReceiveAddress = memberFeignService.getMemberReceiveAddress(memberResponseVo.getId());
            orderConfirmVo.setMemberAddressVos(memberReceiveAddress);
        }, executor);

        CompletableFuture<Void> orderItemTask = CompletableFuture.runAsync(() -> {
            RequestContextHolder.setRequestAttributes( requestAttributes );
            // 2.所有选中的购物项
            //feign在远程调用之前要构造请求，调用很多的拦截器
            List<OrderItemVo> currentUserItems = cartFeignService.getCurrentUserItems();
            orderConfirmVo.setItems(currentUserItems);
        }, executor).thenRunAsync( ()-> {
            List<OrderItemVo> items = orderConfirmVo.getItems();
            List<Long> skuIds = items.stream().map((item) -> item.getSkuId()).collect(Collectors.toList());

            // 调用库存服务，批量查询对应商品是否有货
            R skuHasStock = wareFeignService.getSkuHasStock(skuIds);
            List<SkuStockVo> data = skuHasStock.getData(new TypeReference<List<SkuStockVo>>() {
            });

            if ( data != null && data.size() > 0){
                Map<Long, Boolean> stockMap = data.stream().collect(Collectors.toMap(SkuStockVo::getSkuId, SkuStockVo::getHasStock));
                orderConfirmVo.setStocks( stockMap );
            }


        }, executor);


        // 3. 优惠券（会员积分）
        Integer integration = memberResponseVo.getIntegration();
        orderConfirmVo.setIntegration( integration );

        // todo： 接口幂等性，设置访问token
        String token = UUID.randomUUID().toString().replaceAll("-", "");
        orderConfirmVo.setOrderToken( token );
        redisTemplate.opsForValue().set(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVo.getId(), token, 30, TimeUnit.MINUTES);

        CompletableFuture.allOf( addressTask, orderItemTask).get();

        return orderConfirmVo;
    }


    /**
     * 提交订单
     * @param orderSubmitVo
     * @return
     */
//    @GlobalTransactional
    @Transactional
    @Override
    public SubmitOrderResponseVo submitOrder(OrderSubmitVo orderSubmitVo) {

        orderSubmitVoThreadLocal.set( orderSubmitVo );

        SubmitOrderResponseVo responseVo = new SubmitOrderResponseVo();
        responseVo.setCode(0);

        // 1、获取当前登录用户
        MemberResponseVo memberResponseVo = OrderLoginIntercepter.threadLocal.get();

        // 2、验证token
        String orderToken = orderSubmitVo.getOrderToken();
        String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        Long result = redisTemplate.execute(new DefaultRedisScript<Long>(script, Long.class),
                Arrays.asList(OrderConstant.USER_ORDER_TOKEN_PREFIX + memberResponseVo.getId()),
                orderToken);

        if ( result == 0L){
            // token验证失败
            responseVo.setCode(1);
            return responseVo;
        }

        // 验证成功
        // 1、创建订单、订单项等信息
        OrderCreateTo order =  createOrder();

        // 2、验证价格
        BigDecimal payAmount = order.getOrder().getPayAmount();
        BigDecimal payPrice = orderSubmitVo.getPayPrice();

        if ( Math.abs( payAmount.subtract(payPrice).doubleValue())<0.01){
            // 3、保存订单
            saveOrder( order );

            // 4、库存锁定，只要有异常，回滚订单数据
            //订单号、所有订单项信息
            WareSkuLockVo lockVo = new WareSkuLockVo();
            lockVo.setOrderSn( order.getOrder().getOrderSn() );

            // 获取要锁定的商品数据信息
            List<OrderItemVo> orderItemVos = order.getOrderItems().stream().map(item -> {
                OrderItemVo orderItemVo = new OrderItemVo();
                orderItemVo.setSkuId(item.getSkuId());
                orderItemVo.setCount(item.getSkuQuantity());
                orderItemVo.setTitle(item.getSkuName());
                return orderItemVo;
            }).collect(Collectors.toList());

            lockVo.setLocks( orderItemVos);

            // todo: 调用远程锁定库存的方法
            /*
            出现的问题： 扣减库存成功了，但是由于网络原因超时，出现了异常，导致订单事务回滚，库存事务不回滚
            解决方案： seata（分布式事务）

            为了保证高并发，不建议使用seata，因为是加锁，并行化，提升不了效率，可以通过发消息给库存服务
             */
            R r = wareFeignService.orderLockStock(lockVo);
            if ( r.getCode() == 0 ){
                // 锁定成功
                responseVo.setOrder( order.getOrder() );
                // 定义一个错误，测试一下seata的作用
//                 int i = 1/0;

                // todo: 订单创建成功，发送消息给MQ
                rabbitTemplate.convertAndSend("order-event-exchange", "order.create.order", order.getOrder() );

                // 删除购物车里的数据
                redisTemplate.delete(CartConstant.CART_PREFIX + memberResponseVo.getId() );
//
            }else {
                //锁定失败
                String msg = (String) r.get("msg");
                throw new NoStockException(msg);
            }
        }else {
            // 验价不通过
            responseVo.setCode(2);
            return responseVo;
        }

        return responseVo;
    }


    /**
     * 按照订单号获取订单信息
     * @param orderSn
     * @return
     */
    @Override
    public OrderEntity getOrderByOrderSn(String orderSn) {
        OrderEntity order_sn = baseMapper.selectOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderSn));
        return order_sn;
    }


    /**
     * 关闭订单
     * @param orderEntity
     */
    @Override
    public void closeOrder(OrderEntity orderEntity) {
        // 关闭订单之前先查询一下数据库， 判断此订单状态是否已支付
        OrderEntity orderInfo = this.getOne(new QueryWrapper<OrderEntity>().eq("order_sn", orderEntity.getOrderSn()));

        if ( orderInfo.getStatus().equals( OrderStatusEnum.CREATE_NEW.getCode())){
            // 待付款状态进行关单
            OrderEntity orderUpdate = new OrderEntity();
            orderUpdate.setId( orderInfo.getId() );
            orderUpdate.setStatus( OrderStatusEnum.CANCLED.getCode());
            this.updateById(orderUpdate );

            // 修改状态后，发送消息给MQ
            OrderTo orderTo = new OrderTo();
            BeanUtils.copyProperties( orderInfo, orderTo);

            try {
                // 确保每个消息发送成功，给每个消息做好日志记录，（给数据库保存每一个详细信息）
                // 保存每个消息的详细信息
                rabbitTemplate.convertAndSend("order-event-exchange", "order.release.other", orderTo);
            }catch (Exception e ){
                // 定期扫描数据库，重新发送失败的消息
            }
        }
    }


    /**
     * 获取当前订单的支付信息
     * @param orderSn
     * @return
     */
    @Override
    public PayVo getOrderPay(String orderSn) {
        OrderEntity orderInfo = this.getOrderByOrderSn(orderSn);
        PayVo payVo = new PayVo();
        BeanUtils.copyProperties( orderInfo, payVo );

        // 保留两位小数，向上取整
        BigDecimal bigDecimal = orderInfo.getPayAmount().setScale(2, BigDecimal.ROUND_UP);
        payVo.setTotal_amount( bigDecimal.toString() );

        payVo.setOut_trade_no( orderSn );

        // 查询订单项的数据
        List<OrderItemEntity> orderItemEntities = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", orderSn));
        OrderItemEntity orderItemEntity = orderItemEntities.get(0);
        payVo.setSubject( orderItemEntity.getSkuName());
        payVo.setBody(orderItemEntity.getSkuAttrsVals());

        return payVo;
    }


    /**
     * 获取当前用户所有订单数据
     * @param params
     * @return
     */
    @Override
    public PageUtils queryPageWithItem(Map<String, Object> params) {
        MemberResponseVo memberResponseVo = OrderLoginIntercepter.threadLocal.get();

        IPage<OrderEntity> page = this.page(
                new Query<OrderEntity>().getPage(params),
                new QueryWrapper<OrderEntity>().eq("member_id", memberResponseVo.getId())
                        .orderByDesc("create_time")
        );

        // 遍历订单集合
        List<OrderEntity> orderEntityList = page.getRecords().stream().map(order -> {
            // 根据订单好查询订单项里的数据
            List<OrderItemEntity> orderItemEntities = orderItemService.list(new QueryWrapper<OrderItemEntity>().eq("order_sn", order.getOrderSn()));
            order.setOrderItemEntityList(orderItemEntities);
            return order;
        }).collect(Collectors.toList());
        page.setRecords( orderEntityList);
        return new PageUtils(page);
    }


    /**
     * 处理支付宝的支付结果
     * @param asyncVo
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public String handlePayResult(PayAsyncVo asyncVo) {
        // 保存交易流水
        PaymentInfoEntity paymentInfoEntity = new PaymentInfoEntity();
        paymentInfoEntity.setOrderSn( asyncVo.getOut_trade_no() );
        paymentInfoEntity.setAlipayTradeNo(asyncVo.getTrade_no());
        paymentInfoEntity.setTotalAmount(new BigDecimal(asyncVo.getBuyer_pay_amount()));
        paymentInfoEntity.setSubject(asyncVo.getBody());
        paymentInfoEntity.setPaymentStatus(asyncVo.getTrade_status());
        paymentInfoEntity.setCreateTime(new Date());
        paymentInfoEntity.setCallbackTime(asyncVo.getNotify_time());

        paymentInfoService.save( paymentInfoEntity );

        // 修改订单状态
        String tradeStatus = asyncVo.getTrade_status();

        if ( tradeStatus.equals("TRADE_SUCCESS") || tradeStatus.equals("TRADE_FINISHED")){
            // 支付成功状态
            String orderSn = asyncVo.getOut_trade_no();
            this.updateOrderStatus( orderSn, OrderStatusEnum.PAYED.getCode());
        }

        return "success";
    }


    /**
     * 创建秒杀单
     * @param orderTo
     */
    @Override
    public void createSeckillOrder(SeckillOrderTo orderTo) {
        // 保存订单信息
        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setOrderSn( orderTo.getOrderSn());
        orderEntity.setMemberId(orderEntity.getMemberId());
        orderEntity.setCreateTime( new Date( ));

        BigDecimal totalPrice = orderTo.getSeckillPrice().multiply(BigDecimal.valueOf(orderTo.getNum()));
        orderEntity.setPayAmount(totalPrice);
        orderEntity.setStatus( OrderStatusEnum.CREATE_NEW.getCode());

        // 保存订单
        this.save( orderEntity );

        // 保存订单项信息
        OrderItemEntity orderItem = new OrderItemEntity();
        orderItem.setOrderSn( orderTo.getOrderSn());
        orderItem.setRealAmount( totalPrice);
        orderItem.setSkuQuantity( orderTo.getNum());

        // 保存商品的spu信息
        R spuInfo = productFeignService.getSpuInfoBySkuId(orderTo.getSkuId());
        if ( spuInfo.getCode() == 0 ){
            SpuInfoVo spuInfoData = spuInfo.getData(new TypeReference<SpuInfoVo>() {
            });
            orderItem.setSpuId( spuInfoData.getId());
            orderItem.setSpuName(spuInfoData.getSpuName());
            orderItem.setSpuBrand(spuInfoData.getBrandName());
            orderItem.setCategoryId(spuInfoData.getCatalogId());

            // 保存订单项数据
            orderItemService.save( orderItem );
        }
    }

    /**
     * 修改订单状态
     * @param orderSn
     * @param code
     */
    private void updateOrderStatus(String orderSn, Integer code) {
        baseMapper.updateOrderStatus( orderSn, code );
    }

    /**
     * 保存订单所有数据
     * @param orderCreateTo
     */
    private void saveOrder(OrderCreateTo orderCreateTo) {
        // 获取订单信息
        OrderEntity order = orderCreateTo.getOrder();
        order.setModifyTime( new Date());
        order.setCreateTime( new Date());

        // 保存订单
        baseMapper.insert( order );

        //获取订单项信息
        List<OrderItemEntity> orderItems = orderCreateTo.getOrderItems();
        // 批量保存订单项数据
        orderItemService.saveBatch( orderItems );
    }


    /**
     * 创建订单
     * @return
     */
    private OrderCreateTo createOrder() {
        OrderCreateTo createTo = new OrderCreateTo();

        // 1、生成订单号
        String orderSn = IdWorker.getTimeId();
        OrderEntity orderEntity = buildOrder( orderSn );
        // 2、获取到所有的订单项
        List<OrderItemEntity> orderItemEntities = builderOrderItems( orderSn );
        // 3、验价
        computePrice( orderEntity, orderItemEntities);

        createTo.setOrder( orderEntity );
        createTo.setOrderItems( orderItemEntities );
        return createTo;
    }

    /**
     * 计算价格的方法
     * @param orderEntity
     * @param orderItemEntities
     */
    private void computePrice(OrderEntity orderEntity, List<OrderItemEntity> orderItemEntities) {
        // 总价
        BigDecimal total = new BigDecimal("0.0");
        BigDecimal coupon = new BigDecimal("0.0");
        BigDecimal intergration = new BigDecimal("0.0");
        BigDecimal promotion = new BigDecimal("0.0");

        //积分、成长值
        Integer intergrationTotal = 0;
        Integer growthTotal = 0;

        // 订单总额，叠加每一个订单项的总额信息
        for ( OrderItemEntity orderItem: orderItemEntities ){
            // 优惠价格信息
            coupon = coupon.add( orderItem.getCouponAmount());
            promotion = promotion.add(orderItem.getPromotionAmount());
            intergration = intergration.add( orderItem.getIntegrationAmount());

            //总价
            total = total.add( orderItem.getRealAmount() );

            // 积分信息和成长值信息
            intergrationTotal += orderItem.getGiftIntegration();
            growthTotal += orderItem.getGiftGrowth();
        }

        // 1.订单价格相关的
        orderEntity.setTotalAmount( total );
        // 设置应付总额  商品总价 + 运费
        orderEntity.setPayAmount( total.add( orderEntity.getFreightAmount()));
        orderEntity.setCouponAmount( coupon );
        orderEntity.setPromotionAmount( promotion );
        orderEntity.setIntegrationAmount( intergration );

        // 设置积分成长值信息
        orderEntity.setIntegration( intergrationTotal );
        orderEntity.setGrowth( growthTotal );

        // 设置删除状态(0-未删除  1-已删除)
        orderEntity.setDeleteStatus( 0 );
    }

    /**
     * 构建所有订单项数据
     * @return
     */
    private List<OrderItemEntity> builderOrderItems(String orderSn) {
        List<OrderItemEntity> orderItemEntityList = new ArrayList<>();

        // 确定每个购物项的价格
        List<OrderItemVo> currentUserItems = cartFeignService.getCurrentUserItems();
        if ( currentUserItems != null && currentUserItems.size() > 0 ){
            orderItemEntityList = currentUserItems.stream().map( item -> {
                OrderItemEntity orderItemEntity = builderOrderItem( item );
                orderItemEntity.setOrderSn( orderSn );

                return orderItemEntity;
            }).collect(Collectors.toList());
        }
        return orderItemEntityList;
    }


    /**
     * 构建某一个订单项的数据
     * @param items
     * @return
     */
    private OrderItemEntity builderOrderItem(OrderItemVo items) {

        OrderItemEntity orderItemEntity = new OrderItemEntity();

        // 1.商品的spu信息
        Long skuId = items.getSkuId();

        // 获取spu信息
        R spuInfo = productFeignService.getSpuInfoBySkuId(skuId);
        SpuInfoVo spuInfoData = spuInfo.getData(new TypeReference<SpuInfoVo>() {
        });

        orderItemEntity.setSpuId( spuInfoData.getId() );
        orderItemEntity.setSpuName(spuInfoData.getSpuName());
        orderItemEntity.setSpuBrand(spuInfoData.getBrandName());
        orderItemEntity.setCategoryId(spuInfoData.getCatalogId());

        // 2.商品的sku信息
        orderItemEntity.setSkuId( skuId );
        orderItemEntity.setSkuName( items.getTitle() );
        orderItemEntity.setSkuPic( items.getImage() );
        orderItemEntity.setSkuPrice( items.getPrice() );
        orderItemEntity.setSkuQuantity( items.getCount() );

        // 使用StringUtils.collectionToDelimitedString将list集合转换为String
        String skuAttrValues = StringUtils.collectionToDelimitedString(items.getSkuAttrValues(), ";");
        orderItemEntity.setSkuAttrsVals( skuAttrValues );

        // 3.商品优惠信息（不做）

        // 4.商品的积分信息
        orderItemEntity.setGiftGrowth( items.getPrice().multiply( new BigDecimal(items.getCount())).intValue());
        orderItemEntity.setGiftIntegration( items.getPrice().multiply( new BigDecimal(items.getCount())).intValue());

        // 5.订单项的价格信息
        orderItemEntity.setPromotionAmount( BigDecimal.ZERO);
        orderItemEntity.setCouponAmount( BigDecimal.ZERO );
        orderItemEntity.setIntegrationAmount( BigDecimal.ZERO );

        // 当前订单项的实际金额    总额-各种优惠价格
        // 原来的价格
        BigDecimal origin = orderItemEntity.getSkuPrice().multiply(new BigDecimal(orderItemEntity.getSkuQuantity().toString()));
        // 原价减去优惠价得到最终的价格
        BigDecimal subtract = origin.subtract(orderItemEntity.getCouponAmount())
                .subtract(orderItemEntity.getPromotionAmount())
                .subtract(orderItemEntity.getIntegrationAmount());

        orderItemEntity.setRealAmount( subtract );


        return orderItemEntity;
    }

    /**
     * 构建订单数据
     * @param orderSn
     * @return
     */
    private OrderEntity buildOrder(String orderSn) {
        MemberResponseVo memberResponseVo = OrderLoginIntercepter.threadLocal.get();

        OrderEntity orderEntity = new OrderEntity();
        orderEntity.setMemberId(memberResponseVo.getId());
        orderEntity.setOrderSn(orderSn);
        orderEntity.setMemberUsername(memberResponseVo.getUsername());

        OrderSubmitVo orderSubmitVo = orderSubmitVoThreadLocal.get();

        // 远程调用，获取收货地址和运费信息
        R fareR = wareFeignService.getFare(orderSubmitVo.getAddrId());
        FareVo fareVo = fareR.getData(new TypeReference<FareVo>() {
        });

        //获取到运费信息
        BigDecimal fare = fareVo.getFare();
        orderEntity.setFreightAmount(fare);

        //获取到收货地址信息
        MemberAddressVo address = fareVo.getAddress();
        //设置收货人信息
        orderEntity.setReceiverName(address.getName());
        orderEntity.setReceiverPhone(address.getPhone());
        orderEntity.setReceiverPostCode(address.getPostCode());
        orderEntity.setReceiverProvince(address.getProvince());
        orderEntity.setReceiverCity(address.getCity());
        orderEntity.setReceiverRegion(address.getRegion());
        orderEntity.setReceiverDetailAddress(address.getDetailAddress());

        //设置订单相关的状态信息
        orderEntity.setStatus(OrderStatusEnum.CREATE_NEW.getCode());
        orderEntity.setAutoConfirmDay(7);
        orderEntity.setConfirmStatus(0);
        return orderEntity;
    }

}