package com.atguigu.gulimall.authserver.controller;


import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.AuthLoginConstant;
import com.atguigu.common.utils.HttpUtils;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVo;
import com.atguigu.gulimall.authserver.feign.MemberFeignService;
import com.atguigu.gulimall.authserver.vo.SocialUser;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
public class OAuth2Controller {
    @Autowired
    private MemberFeignService memberFeignService;

    @GetMapping("/oauth2.0/weibo/success")
    public String weibo(@RequestParam("code") String code, HttpSession session) throws Exception {

        Map<String,String> map = new HashMap<>();
        map.put("client_id", "2107641897");
        map.put("client_secret", "aac9f0d3dcf80922644e3722980f4a02");
        map.put("grant_type", "authorization_code");
        map.put("redirect_uri", "http://auth.gulimall.com/oauth2.0/weibo/success");
        map.put("code", code);

        // https://api.weibo.com/oauth2/access_token
        //用 code 换取授权 access_token，该步需在服务端完成
        HttpResponse response = HttpUtils.doPost("https://api.weibo.com", "/oauth2/access_token", "post", new HashMap<>(), map, new HashMap<>());

        if ( response.getStatusLine().getStatusCode() == 200 ){
            // 成功
            String json = EntityUtils.toString(response.getEntity());
            SocialUser socialUser = JSON.parseObject(json, SocialUser.class);
            System.out.println( socialUser );

            // 调用远程服务进行  登录或注册
            R oauthLogin = memberFeignService.oauthLogin(socialUser);
            if ( oauthLogin.getCode() == 0 ){
                // 注册或登录成功
                MemberResponseVo memberResponseVo = oauthLogin.getData("data", new TypeReference<MemberResponseVo>(){});
                log.info( memberResponseVo.toString() );

                // 登录成功，朓转到主界面
                //1、第一次使用session，命令浏览器保存卡号，JSESSIONID这个cookie
                //以后浏览器访问哪个网站就会带上这个网站的cookie
                //TODO 1、默认发的令牌。当前域（解决子域session共享问题）
                //TODO 2、使用JSON的序列化方式来序列化对象到Redis中
                session.setAttribute(AuthLoginConstant.LOGIN_USER, memberResponseVo);
                return "redirect:http://gulimall.com";
            }else {
                // 登录失败，直接返回登录界面
                return "redirect:http://auth.gulimall.com/login.html";
            }
        }else {
            // 授权失败，直接返回登录界面
            return "redirect:http://auth.gulimall.com/login.html";
        }
    }

}
