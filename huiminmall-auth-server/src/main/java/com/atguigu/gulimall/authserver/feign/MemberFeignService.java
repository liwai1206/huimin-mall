package com.atguigu.gulimall.authserver.feign;


import com.atguigu.common.utils.R;
import com.atguigu.gulimall.authserver.vo.SocialUser;
import com.atguigu.gulimall.authserver.vo.UserLoginVo;
import com.atguigu.gulimall.authserver.vo.UserRegisterVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient("gulimall-member")
public interface MemberFeignService {
    @PostMapping("/member/member/register")
    public R memberRegister(@RequestBody UserRegisterVo vo);

    @PostMapping("/member/member/login")
    public R memberLogin(@RequestBody UserLoginVo vo);

    @PostMapping("/member/member/oauth2/login")
    public R oauthLogin(@RequestBody SocialUser socialUser) throws Exception;
}
