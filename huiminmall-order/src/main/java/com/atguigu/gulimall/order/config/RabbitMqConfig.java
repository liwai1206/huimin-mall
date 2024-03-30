package com.atguigu.gulimall.order.config;


import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
public class RabbitMqConfig {

//    @Autowired
//    private RabbitTemplate rabbitTemplate;
//
//    /**
//     * rabbitmq的对象序列化器
//     * @return
//     */
    @Bean
    public MessageConverter messageConverter(){
        return new Jackson2JsonMessageConverter();
    }
//
//
//    /**
//     * 定制RabbitTemplate
//     * 1、服务收到消息就会回调
//     *      1、spring.rabbitmq.publisher-confirms: true
//     *      2、设置确认回调
//     * 2、消息正确抵达队列就会进行回调
//     *      1、spring.rabbitmq.publisher-returns: true
//     *         spring.rabbitmq.template.mandatory: true
//     *      2、设置确认回调ReturnCallback
//     *
//     * 3、消费端确认(保证每个消息都被正确消费，此时才可以broker删除这个消息)
//     *
//     */
//    @PostConstruct
//    public void callback(){
//        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
//            /**
//             * 服务端成功接收到消息后进入回调
//             * 接收到只能表示 message 已经到达服务器，并不能保证消息一定会被投递到目标 queue 里。所以需要用到接下来的 returnCallback 。
//             * @param correlationData 用来表示当前消息唯一性。
//             * @param ack
//             * @param cause
//             */
//            @Override
//            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
//                System.out.println("confirm...correlationData["+correlationData+"]==>ack:["+ack+"]==>cause:["+cause+"]");
//            }
//        });
//
//
//        rabbitTemplate.setReturnCallback(new RabbitTemplate.ReturnCallback() {
//            /**
//             * 消息没有传递到指定的队列，就触发这个回调
//             * @param message   投递失败的消息详情信息
//             * @param replyCode 响应状态码
//             * @param replyText 响应内容
//             * @param exchange  当时这个消息发送的交换机
//             * @param routingKey    当时这个消息发送的路由键
//             */
//            @Override
//            public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
//                System.out.println("Fail Message["+message+"]==>replyCode["+replyCode+"]" +
//                        "==>replyText["+replyText+"]==>exchange["+exchange+"]==>routingKey["+routingKey+"]");
//            }
//        });
//    }
}
