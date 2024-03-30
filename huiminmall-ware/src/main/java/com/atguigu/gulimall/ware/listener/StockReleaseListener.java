package com.atguigu.gulimall.ware.listener;


import com.atguigu.common.to.OrderTo;
import com.atguigu.common.to.mq.StockLockedTo;
import com.atguigu.gulimall.ware.service.WareSkuService;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RabbitListener(queues = {"stock.release.stock.queue"})
public class StockReleaseListener {

    @Autowired
    private WareSkuService wareSkuService;

    /*
    1. 库存自动解锁
        下单成功，库存锁定成功，接下来的业务调用失败，导致订单回滚。之前锁定的库存就要自动解锁
    2. 订单失败，
        库存锁定失败
        只要解锁库存的消息失败，一定要告诉服务解锁失败
     */
    @RabbitHandler
    public void handleStockLockedRelease(StockLockedTo to, Message message, Channel channel) throws IOException {
        log.info("-------收到解锁库存的消息-------------");

        try {
            // 解锁库存
            wareSkuService.unLockStock( to );
            channel.basicAck( message.getMessageProperties().getDeliveryTag(), false);
        } catch (IOException e) {
            // 解锁失败，重新入队
            channel.basicReject(  message.getMessageProperties().getDeliveryTag(), true);
        }
    }

    @RabbitHandler
    public void handleOrderCloseRelease(OrderTo orderTo, Message message, Channel channel) throws IOException {
        log.info("******收到订单关闭，准备解锁库存的信息******");

        try {
            wareSkuService.unLockStock(  orderTo );
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
        }catch( Exception e ){
            channel.basicReject( message.getMessageProperties().getDeliveryTag(), true);
        }
    }

}
