package com.atguigu.gulimall.coupon;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
public class SpringCouponApplication {
    public static void main(String[] args) {
        SpringApplication.run( SpringCouponApplication.class,args);
    }
}
