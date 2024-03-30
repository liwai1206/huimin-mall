package com.atguigu.gulimall.authserver.controller;


import cn.hutool.Hutool;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.RandomUtil;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.AuthLoginConstant;
import com.atguigu.common.constant.ThirdPartSmsConstant;
import com.atguigu.common.exception.BizCodeEnume;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVo;
import com.atguigu.gulimall.authserver.feign.MemberFeignService;
import com.atguigu.gulimall.authserver.feign.ThirdPartSmsFeignService;
import com.atguigu.gulimall.authserver.vo.UserLoginVo;
import com.atguigu.gulimall.authserver.vo.UserRegisterVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
public class LoginController {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ThirdPartSmsFeignService thirdPartSmsFeignService;

    @Autowired
    private MemberFeignService memberFeignService;


    /**
     * 发送验证码
     * @param phone
     * @return
     */
    @ResponseBody
    @GetMapping("/sms/sendCode")
    public R sendCode(@RequestParam("phone") String phone){

        // 1.防刷验证码
        String redis_code = redisTemplate.opsForValue().get(ThirdPartSmsConstant.REDIS_SMS_CODE_PREFIX + phone);
        if ( !StringUtils.isEmpty( redis_code )){
            long l = Long.parseLong(redis_code.split("_")[1]);
            if ( System.currentTimeMillis() - l < 60 * 1000){
                // 防止一分钟以内连续发送验证码
                return R.error(BizCodeEnume.SMS_CODE_EXCEPTION.getCode(), BizCodeEnume.SMS_CODE_EXCEPTION.getMsg());
            }

        }

        // 发送验证码,  格式： phone:code:13548896936 -> 1234_23225435435
        String code = RandomUtil.randomNumbers(4);
        thirdPartSmsFeignService.sendCode( phone, code);

        //2.设置过期时间,十分钟有效
        redisTemplate.opsForValue().set(ThirdPartSmsConstant.REDIS_SMS_CODE_PREFIX+phone, code+ "_" + System.currentTimeMillis(), 10, TimeUnit.MINUTES);

        return R.ok();
    }


    /**
     * 注册
     * @param registerVo    封装页面传过来的数据
     * @param result        RS3验证的结果
     * @param redirectAttributes    重定向携带数据
     * @return
     */
    @PostMapping("/register")
    public String register(@Valid UserRegisterVo registerVo, BindingResult result, RedirectAttributes redirectAttributes){
        // 如果数据有误，则将错误数据传回给注册页面
        if ( result.hasErrors() ){
            Map<String, String> errors =
                    result.getFieldErrors().stream().collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));

            redirectAttributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.gulimall.com/reg.html";
        }

        // 如果没有错误，则进行注册
        // 验证码是否正确
        String code = registerVo.getCode();
        String redis_code = redisTemplate.opsForValue().get(ThirdPartSmsConstant.REDIS_SMS_CODE_PREFIX + registerVo.getPhone());
        if ( !StringUtils.isEmpty( redis_code )){
            if ( redis_code.split("_")[0].equals( code )){
                // 验证通过,删除redis中的验证码
                redisTemplate.delete(ThirdPartSmsConstant.REDIS_SMS_CODE_PREFIX + registerVo.getPhone());
                // 调用member服务，进行注册
                R register = memberFeignService.memberRegister(registerVo);
                if ( register.getCode()== 0 ){
                    // 注册成功
                    return "redirect:http://auth.gulimall.com/login.html";
                }else {
                    // 失败, 封装错误信息，并返回注册页面
                    Map<String,String> errors = new HashMap<>();
                    errors.put("msg", register.getData("msg", new TypeReference<String>(){}));
                    redirectAttributes.addFlashAttribute("errors", errors);
                    return "redirect:http://auth.gulimall.com/reg.html";
                }
            }else {
                // 验证码不正确,返回错误信息到注册页面
                HashMap<String, String> errors = new HashMap<>();
                errors.put("code", "验证码错误");
                redirectAttributes.addFlashAttribute("errors", errors);
                return "redirect:http://auth.gulimall.com/reg.html";
            }
        }else {
            // 验证码不正确,返回错误信息到注册页面
            HashMap<String, String> errors = new HashMap<>();
            errors.put("code", "验证码错误");
            redirectAttributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.gulimall.com/reg.html";
        }
    }


    /**
     * 登录
     * @param userLoginVo
     * @param validResult
     * @param redirectAttributes
     * @return
     */
    @PostMapping("/login")
    public String login(@Valid UserLoginVo userLoginVo, BindingResult validResult, RedirectAttributes redirectAttributes, HttpSession session){

        if ( validResult.hasFieldErrors() ){
            // 如果校验不通过
            Map<String, String> errors = validResult.getFieldErrors().stream().collect(Collectors.toMap(FieldError::getField, FieldError::getDefaultMessage));
            redirectAttributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.gulimall.com/login.html";
        }

        R login = memberFeignService.memberLogin(userLoginVo);
        if ( login.getCode() == 0 ){
            // 成功
            MemberResponseVo memberResponseVo = login.getData("data", new TypeReference<MemberResponseVo>() {});
            session.setAttribute(AuthLoginConstant.LOGIN_USER, memberResponseVo);
            return "redirect:http://gulimall.com";
        }else {
            // 失败
            Map<String,String> errors = new HashMap<>();
            errors.put("msg", login.getData("msg", new TypeReference<String>(){}));
            redirectAttributes.addFlashAttribute("errors", errors);
            return "redirect:http://auth.gulimall.com/login.html";
        }
    }

    @GetMapping("/login.html")
    public String loginPage( HttpSession session ){
        Object attribute = session.getAttribute(AuthLoginConstant.LOGIN_USER);
        if ( attribute == null ){
            return "login";
        }else {
            return "redirect:http://gulimall.com";
        }
    }
}
