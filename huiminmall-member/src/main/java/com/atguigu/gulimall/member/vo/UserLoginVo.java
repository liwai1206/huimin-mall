package com.atguigu.gulimall.member.vo;


import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class UserLoginVo {

    private String loginAcct;

    private String password;

}
