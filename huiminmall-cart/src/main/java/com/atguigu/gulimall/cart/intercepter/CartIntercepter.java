package com.atguigu.gulimall.cart.intercepter;

import com.alibaba.fastjson.JSON;
import com.atguigu.common.constant.AuthLoginConstant;
import com.atguigu.common.constant.CartConstant;
import com.atguigu.common.vo.MemberResponseVo;
import com.atguigu.gulimall.cart.to.UserInfoTo;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.UUID;

/**
 * 在执行目标方法之前，判断用户的登录状态.并封装传递给controller目标请求
 */
public class CartIntercepter implements HandlerInterceptor {

    public static ThreadLocal<UserInfoTo> threadLocal = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 封装UserInfoTo
        UserInfoTo userInfoTo = new UserInfoTo();
        HttpSession session = request.getSession();
        Object attribute = session.getAttribute(AuthLoginConstant.LOGIN_USER);

        if ( attribute != null ){
            // 已经登录了
            String s = JSON.toJSONString(attribute);
            MemberResponseVo member = JSON.parseObject( s, MemberResponseVo.class );
            userInfoTo.setUserId( member.getId() );
        }

        // 遍历所有cookies，判断是否已经存在user-key的cookie，如果存在则说明已经是一个临时用户，否则不是
        Cookie[] cookies = request.getCookies();
        for (Cookie cookie : cookies) {
            if ( cookie.getName().equals(CartConstant.TEMP_USER_COOKIE_NAME)){
                // 是一个临时用户
                userInfoTo.setTempUser( true );
                userInfoTo.setUserKey( cookie.getValue() );
            }
        }

        if ( !userInfoTo.getTempUser() ){
            // 如果还不是一个临时用户，说明是第一次进入网站，则需要创建一个临时用户的cookie
            String uuid = UUID.randomUUID().toString();
            userInfoTo.setUserKey( uuid );
        }

        threadLocal.set(userInfoTo);
        return true;
    }





    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {

        UserInfoTo userInfoTo = threadLocal.get();
        if ( !userInfoTo.getTempUser() ){
            Cookie cookie = new Cookie(CartConstant.TEMP_USER_COOKIE_NAME, userInfoTo.getUserKey());
            cookie.setMaxAge( CartConstant.TEMP_USER_COOKIE_TIMEOUT );
            cookie.setDomain("gulimall.com");
            response.addCookie(cookie);
        }

    }
}
