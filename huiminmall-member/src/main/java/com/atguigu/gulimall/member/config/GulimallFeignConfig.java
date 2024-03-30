package com.atguigu.gulimall.member.config;


import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Configuration
public class GulimallFeignConfig {

    /**
     * 由于Feign远程调用时，请求头信息全部丢失，因此被调用的服务无法获取到请求头中的登录session
     * 在feign源码中，发送远程调用过程中会调用很多的拦截器，因此我们可以通过注入一个拦截器，并
     * 将请求头中有用的信息进行同步，保证被调用的服务可以成功获取到
     * @return
     */
    @Bean
    public RequestInterceptor requestInterceptor(){
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate requestTemplate) {
                // 1.使用RequestContextHolder获取到进来的请求数据
                ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

                if ( requestAttributes != null ){
                    // 老请求
                    HttpServletRequest request = requestAttributes.getRequest();

                    if ( request != null ){
                        // 2.同步请求头的数据，主要是Cookie
                        // 将老请求的请求头的cookie值放在新请求，进行同步
                        String cookie = request.getHeader("Cookie");
                        requestTemplate.header("Cookie", cookie);
                    }
                }
            }
        };
    }
}
