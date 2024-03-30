package com.atguigu.gulimall.order.config;


import com.atguigu.gulimall.order.intercepter.OrderLoginIntercepter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OrderIntercepter implements WebMvcConfigurer {

    @Autowired
    public OrderLoginIntercepter orderLoginIntercepter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(orderLoginIntercepter).addPathPatterns("/**");
    }
}
