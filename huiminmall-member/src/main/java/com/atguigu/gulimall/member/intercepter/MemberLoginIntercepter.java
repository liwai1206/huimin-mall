package com.atguigu.gulimall.member.intercepter;


import com.alibaba.fastjson.JSON;
import com.atguigu.common.constant.AuthLoginConstant;
import com.atguigu.common.vo.MemberResponseVo;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class MemberLoginIntercepter implements HandlerInterceptor {

    public static ThreadLocal<MemberResponseVo> threadLocal = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String requestURI = request.getRequestURI();
        AntPathMatcher antPathMatcher = new AntPathMatcher();
        boolean match = antPathMatcher.match("/member/**", requestURI);
        if ( match ){
            return true;
        }


        Object attribute =  request.getSession().getAttribute(AuthLoginConstant.LOGIN_USER);
        String s = JSON.toJSONString(attribute);
        MemberResponseVo memberResponseVo = JSON.parseObject(s, MemberResponseVo.class);
        if ( memberResponseVo != null ){
            // 已登录
            threadLocal.set( memberResponseVo );
            return true;
        }else {
            // 未登录
            request.getSession().setAttribute("msg", "请先登录");
            response.sendRedirect("http://auth.gulimall.com/login.html");
            return false;
        }
    }
}
