package com.atguigu.gulimall.authserver.vo;


import lombok.Data;

import javax.validation.constraints.NotEmpty;

@Data
public class UserLoginVo {

    @NotEmpty(message = "账号不能为空")
    private String loginAcct;

    @NotEmpty(message = "密码不能为空")
    private String password;

}
