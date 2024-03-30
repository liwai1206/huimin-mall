package com.atguigu.gulimall.order.service.impl;


import com.atguigu.gulimall.order.entity.OrderEntity;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;


@Slf4j
@Component
//@RabbitListener(queues = {"hello-java-queue"})
public class RabbitServiceImp {

//    @RabbitHandler
    public void receiveMessage(Message message, OrderEntity orderEntity, Channel channel){
        log.info("接收到消息：{}", orderEntity.toString());

        try {
            // 确认消息
            channel.basicAck( message.getMessageProperties().getDeliveryTag(), false);
            // 丢弃消息
//            channel.basicReject(message.getMessageProperties().getDeliveryTag(), false);
//            // 不确认，也不丢弃消息，而是将此消息重新入队
//            channel.basicNack(message.getMessageProperties().getDeliveryTag(), false, true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
