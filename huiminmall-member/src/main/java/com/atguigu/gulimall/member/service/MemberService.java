package com.atguigu.gulimall.member.service;

import com.atguigu.gulimall.member.vo.SocialUser;
import com.atguigu.gulimall.member.vo.UserLoginVo;
import com.atguigu.gulimall.member.vo.UserRegisterVo;
import com.baomidou.mybatisplus.extension.service.IService;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.gulimall.member.entity.MemberEntity;

import java.util.Map;

/**
 * ??Ա
 *
 * @author 22wli
 * @email 22wli@gmail.com
 * @date 2023-10-14 15:30:09
 */
public interface MemberService extends IService<MemberEntity> {

    PageUtils queryPage(Map<String, Object> params);

    void register(UserRegisterVo vo);

    MemberEntity login(UserLoginVo vo);

    MemberEntity oauthLogin(SocialUser socialUser) throws Exception;
}

