package com.atguigu.gulimall.member.config;


import com.atguigu.gulimall.member.intercepter.MemberLoginIntercepter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MemberIntercepter implements WebMvcConfigurer {

    @Autowired
    private MemberLoginIntercepter memberLoginIntercepter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(memberLoginIntercepter).addPathPatterns("/**");
    }
}
