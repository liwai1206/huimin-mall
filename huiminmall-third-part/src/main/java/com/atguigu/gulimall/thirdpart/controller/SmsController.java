package com.atguigu.gulimall.thirdpart.controller;


import com.atguigu.common.utils.R;
import com.atguigu.gulimall.thirdpart.component.SmsComponent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@RestController
public class SmsController {

    @Autowired
    private SmsComponent smsComponent;

    @GetMapping("/sms/sendCode")
    public R sendCode(@RequestParam("phone") String phone, @RequestParam("code") String code){
        smsComponent.sendCode( phone, code);
        return R.ok();
    }

}
