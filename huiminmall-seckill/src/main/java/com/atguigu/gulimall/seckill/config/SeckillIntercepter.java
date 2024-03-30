package com.atguigu.gulimall.seckill.config;


import com.atguigu.gulimall.seckill.intercepter.SeckillLoginIntercepter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SeckillIntercepter implements WebMvcConfigurer {

    @Autowired
    public SeckillLoginIntercepter seckillLoginIntercepter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(seckillLoginIntercepter).addPathPatterns("/**");
    }
}
