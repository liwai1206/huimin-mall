package com.atguigu.gulimall.member.web;


import com.atguigu.common.utils.R;
import com.atguigu.gulimall.member.feign.OrderFeignService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

@Controller
public class MemberWebController {

    @Autowired
    private OrderFeignService orderFeignService;

    @GetMapping("/memberOrder.html")
    public String memberOrderPage(@RequestParam(value = "pageNum", required = false, defaultValue = "0") Integer pageNum,
                                  Model model, HttpServletRequest request){

        // 获取支付宝传过来的所有请求数据
        // 验证签名

        // 查出当前登录用户的所有订单列表数据
        Map<String, Object> page = new HashMap<>();
        page.put("page", pageNum.toString());

        // 远程调用订单服务查询订单数据
        R r = orderFeignService.listWithItem(page);

        model.addAttribute("orders", r);

        return "orderList";
    }

}
