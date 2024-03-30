package com.atguigu.gulimall.ware.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.to.OrderTo;
import com.atguigu.common.to.mq.StockDetailTo;
import com.atguigu.common.to.mq.StockLockedTo;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.ware.entity.WareOrderTaskDetailEntity;
import com.atguigu.gulimall.ware.entity.WareOrderTaskEntity;
import com.atguigu.gulimall.ware.feign.OrderFeignService;
import com.atguigu.gulimall.ware.feign.ProductFeignService;
import com.atguigu.gulimall.ware.service.WareOrderTaskDetailService;
import com.atguigu.gulimall.ware.service.WareOrderTaskService;
import com.atguigu.gulimall.ware.vo.OrderItemVo;
import com.atguigu.gulimall.ware.vo.OrderVo;
import com.atguigu.gulimall.ware.vo.SkuHasStockVo;
import com.atguigu.gulimall.ware.vo.WareSkuLockVo;
import lombok.Data;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.WareSkuDao;
import com.atguigu.gulimall.ware.entity.WareSkuEntity;
import com.atguigu.gulimall.ware.service.WareSkuService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("wareSkuService")
public class WareSkuServiceImpl extends ServiceImpl<WareSkuDao, WareSkuEntity> implements WareSkuService {

    @Autowired
    private WareSkuDao wareSkuDao;

    @Autowired
    private WareOrderTaskService wareOrderTaskService;

    @Autowired
    private WareOrderTaskDetailService wareOrderTaskDetailService;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private OrderFeignService orderFeignService;

    @Autowired
    private RabbitTemplate rabbitTemplate;


    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<WareSkuEntity> page = this.page(
                new Query<WareSkuEntity>().getPage(params),
                new QueryWrapper<WareSkuEntity>()
        );

        return new PageUtils(page);
    }

    @Override
    public void addStock(Long skuId, Long wareId, Integer skuNum) {
        // 首先判断该数据是否存在
        List<WareSkuEntity> skuEntityList = baseMapper.selectList(new QueryWrapper<WareSkuEntity>().eq("sku_id", skuId).eq("ware_id", wareId));
        if ( skuEntityList == null || skuEntityList.size() <= 0 ){
            // 不存在，则先添加
            WareSkuEntity wareSkuEntity = new WareSkuEntity();
            wareSkuEntity.setSkuId( skuId );
            wareSkuEntity.setWareId( wareId );
            wareSkuEntity.setStock( skuNum );
            wareSkuEntity.setStockLocked( 0 );

            try {
                R info = productFeignService.info(skuId);
                if ( info.getCode() == 0 ){
                    wareSkuEntity.setSkuName((String) ((Map<String, Object>)info.get("skuInfo")).get("skuName"));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            baseMapper.insert( wareSkuEntity );
        }else {
            // 存在，则更新
            baseMapper.updateStock(skuId, wareId, skuNum);
        }


    }

    @Override
    public List<SkuHasStockVo> getSkuHasStock(List<Long> skuIds) {

        List<SkuHasStockVo> skuHasStockVos = skuIds.stream().map(id -> {
            SkuHasStockVo skuHasStockVo = new SkuHasStockVo();
            skuHasStockVo.setSkuId(id);

            Long count = baseMapper.selectStock(id);
            skuHasStockVo.setHasStock( count == null? false:count>0 );
            return skuHasStockVo;
        }).collect(Collectors.toList());

        return skuHasStockVos;
    }

    /**
     * 为某个订单锁定库存
     * @param vo
     * @return
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean orderLockStock(WareSkuLockVo vo) {
        // 保存库存工作单详情信息
        WareOrderTaskEntity wareOrderTaskEntity = new WareOrderTaskEntity();
        wareOrderTaskEntity.setOrderSn( vo.getOrderSn() );
        wareOrderTaskEntity.setCreateTime( new Date());
        wareOrderTaskService.save( wareOrderTaskEntity );

        // 1.按照下单的收货地址，找到一个就近仓库，锁定库存
        // 2.找到每个商品在哪个仓库有库存
        List<OrderItemVo> locks = vo.getLocks();

        List<SkuWareHasStock> collect = locks.stream().map(item -> {
            SkuWareHasStock stock = new SkuWareHasStock();
            Long skuId = item.getSkuId();
            stock.setSkuId(skuId);
            stock.setNum(item.getCount());

            // 查询这个商品在哪个仓库有库存
            List<Long> wareIdList = wareSkuDao.listWareIdHasSkuStock(skuId);
            stock.setWareId(wareIdList);

            return stock;
        }).collect(Collectors.toList());

        // 锁定库存
        for (SkuWareHasStock hasStock : collect) {
            boolean skuStocked = false;
            Long skuId = hasStock.getSkuId();
            List<Long> wareIds = hasStock.getWareId();

            if (StringUtils.isEmpty( wareIds )){
                // 所有仓库都没有这个商品的库存
                throw new NoStockException(skuId);
            }

            /*
            1. 如果每个商品都锁定成功，将当前商品锁定了几件的工作单记录发送给MQ
            2. 锁定失败，前面保存的工作单信息都回滚了。发送出去的消息，即使要解锁库存，由于数据库查不到指定的id，所以就不用解锁

             */
            for (Long wareId : wareIds) {
                // 锁定成功就返回1，失败就返回0
                Long count = wareSkuDao.lockSkuStock( skuId, wareId, hasStock.getNum() );
                if ( count == 1 ){
                    // 成功
                    skuStocked = true;
                    WareOrderTaskDetailEntity taskDetailEntity = WareOrderTaskDetailEntity.builder()
                            .skuId(skuId)
                            .skuName("")
                            .skuNum(hasStock.getNum())
                            .taskId(wareOrderTaskEntity.getId())
                            .wareId(wareId)
                            .lockStatus(1)
                            .build();

                    wareOrderTaskDetailService.save( taskDetailEntity );

                    // todo: 告诉MQ库存锁定成功
                    StockLockedTo stockLockedTo = new StockLockedTo();
                    stockLockedTo.setId( wareOrderTaskEntity.getId());
                    StockDetailTo detailTo = new StockDetailTo();
                    BeanUtils.copyProperties(taskDetailEntity, detailTo );
                    stockLockedTo.setDetailTo( detailTo );

                    rabbitTemplate.convertAndSend("stock-event-exchange", "stock.locked", stockLockedTo);
                    break;
                }else {
                    // 当前仓库锁失败，继续下一个仓库
                }
            }

            if ( skuStocked == false ){
                // 当前商品在所有仓库都没有被锁住
                throw new NoStockException(skuId);
            }
        }

        // 全部锁住了
        return true;
    }



    @Override
    public void unLockStock(StockLockedTo to) {
        // 库存工作单id
        StockDetailTo detailTo = to.getDetailTo();
        Long detailId = detailTo.getId();

        /*
        解锁
           1。 查询数据库关于这个的订单锁定库存信息
                如果有： 证明库存锁定成功了
                    解锁： 订单状态
                        1. 没有这个订单，必须解锁库存
                        2. 有这个订单，不一定要解锁库存
                            订单状态：
                                已取消： 解锁库存
                                已支付： 不能解锁库存
         */
        WareOrderTaskDetailEntity taskDetailEntity = wareOrderTaskDetailService.getById(detailId);
        if ( taskDetailEntity != null ){
            //有： 证明库存锁定成功了
            // 查询工作单的信息
            Long id = to.getId();
            WareOrderTaskEntity orderTaskEntity = wareOrderTaskService.getById(id);
            // 获取订单号，查询订单状态
            String orderSn = orderTaskEntity.getOrderSn();
            // 远程查询订单信息
            R r = orderFeignService.getOrderStatus(orderSn);
            if ( r.getCode() == 0 ){
                // 成功
                OrderVo orderVo = r.getData("data", new TypeReference<OrderVo>() {
                });

                // 判断订单状态是否已取消或者支付或者订单不存在
                if ( orderVo == null || orderVo.getStatus() == 4 ){
                    // 订单已取消，才能解锁库存
                    if ( taskDetailEntity.getLockStatus() == 1 ){
                        // 当前库存工作单详情状态1，已锁定，但是未解锁才能解锁
                        unLockStock(detailTo.getSkuId(), detailTo.getWareId(), detailTo.getSkuNum(), detailId);
                    }
                }
            }else {
                // 远程调用失败
                // 消息拒绝后重新入队，让别人继续消费
                throw new RuntimeException("远程调用服务失败");
            }
        }else {
            // 没有订单锁定库存信息
            // 无需解锁
        }
    }


    /**
     * 防止订单服务卡顿，导致订单状态消息一直改不了，库存优先到期，查订单状态新建，什么都不处理
     *
     * 导致卡顿的订单吗，永远都不能解锁库存
     * @param orderTo
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void unLockStock(OrderTo orderTo) {

        String orderSn = orderTo.getOrderSn();
        //查一下最新的库存解锁状态，防止重复解锁库存
        WareOrderTaskEntity orderTaskEntity = wareOrderTaskService.getOrderTaskByOrderSn(orderSn);

        //按照工作单的id找到所有 没有解锁的库存，进行解锁
        Long id = orderTaskEntity.getId();
        List<WareOrderTaskDetailEntity> list = wareOrderTaskDetailService.list(new QueryWrapper<WareOrderTaskDetailEntity>()
                .eq("task_id", id).eq("lock_status", 1));

        for (WareOrderTaskDetailEntity taskDetailEntity : list) {
            unLockStock(taskDetailEntity.getSkuId(),
                    taskDetailEntity.getWareId(),
                    taskDetailEntity.getSkuNum(),
                    taskDetailEntity.getId());
        }
    }



    /**
     * 解锁库存的方法
     * @param skuId
     * @param wareId
     * @param skuNum
     * @param detailId
     */
    private void unLockStock(Long skuId, Long wareId, Integer skuNum, Long detailId) {
        // 库存解锁
        wareSkuDao.unLockStock( skuId, wareId, skuNum);

        // 更新工作单状态
        WareOrderTaskDetailEntity taskDetailEntity = new WareOrderTaskDetailEntity();
        taskDetailEntity.setId( detailId );
        taskDetailEntity.setLockStatus(2); // 已解锁
        wareOrderTaskDetailService.updateById( taskDetailEntity );
    }

    @Data
    class SkuWareHasStock {
        private Long skuId;
        private Integer num;
        private List<Long> wareId;
    }
}