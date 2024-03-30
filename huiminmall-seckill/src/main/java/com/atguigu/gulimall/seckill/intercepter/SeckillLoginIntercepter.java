package com.atguigu.gulimall.seckill.intercepter;


import com.alibaba.fastjson.JSON;
import com.atguigu.common.constant.AuthLoginConstant;
import com.atguigu.common.vo.MemberResponseVo;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

@Component
public class SeckillLoginIntercepter implements HandlerInterceptor {

    public static ThreadLocal<MemberResponseVo> threadLocal = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        String requestURI = request.getRequestURI();
        AntPathMatcher antPathMatcher = new AntPathMatcher();
//        boolean match = antPathMatcher.match("/kill", requestURI);
        boolean match = antPathMatcher.match("/**", requestURI);
        if ( match  ){
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
//            response.setContentType("text/html;charset=UTF-8");
//            PrintWriter out = response.getWriter();
//            out.println("<script>alert('请先进行登录，再进行后续操作！');location.href='http://auth.gulimall.com/login.html'</script>");
            request.getSession().setAttribute("msg", "请先登录");
            response.sendRedirect("http://auth.gulimall.com/login.html");
            return false;
        }
    }
}
