package com.atguigu.gulimall.search.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
public class GulimallSessionConfig {

    @Bean
    public CookieSerializer cookieSerializer(){
        DefaultCookieSerializer defaultCookieSerializer = new DefaultCookieSerializer();

        defaultCookieSerializer.setCookieName("GULISESSION");
        // 配置session的作用范围
        defaultCookieSerializer.setDomainName("gulimall.com");
        return defaultCookieSerializer;
    }


    // 配置springsession保存对象到redis中时的序列化器
    @Bean
    public RedisSerializer<Object> springSessionDefaultRedisSerializer(){
        return new Jackson2JsonRedisSerializer<Object>(Object.class);
    }

}
