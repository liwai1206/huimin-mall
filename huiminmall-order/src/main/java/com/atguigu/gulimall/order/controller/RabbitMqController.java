package com.atguigu.gulimall.order.controller;


import com.atguigu.gulimall.order.entity.OrderEntity;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class RabbitMqController {

    @Autowired
    AmqpAdmin amqpAdmin;

    @Autowired
    RabbitTemplate rabbitTemplate;

    @GetMapping("/createExchange")
    public String createExchange(){
        Exchange exchange = new DirectExchange("hello-java-exchange", true, false, null);
        amqpAdmin.declareExchange( exchange );

        return "ok";
    }

    @GetMapping("/createQueue")
    public String createQueue(){
        Queue queue = new Queue("hello-java-queue", true, false, false, null);
        amqpAdmin.declareQueue( queue );

        return "ok";
    }


    @GetMapping("/createBinding")
    public String createBinding(){
        Binding binding = new Binding("hello-java-queue", Binding.DestinationType.QUEUE, "hello-java-exchange", "hello-java", null);
        amqpAdmin.declareBinding( binding );

        return "ok";
    }

    @GetMapping("/sendMessage")
    public String sendMessage(){
        for (int i = 0; i < 10; i++) {
            OrderEntity orderEntity = new OrderEntity();
            orderEntity.setOrderSn(UUID.randomUUID().toString() + "-----" + i);

            rabbitTemplate.convertAndSend("hello-java-exchange", "hello-java", orderEntity);
        }

        return "消息发送成功";
    }
}
