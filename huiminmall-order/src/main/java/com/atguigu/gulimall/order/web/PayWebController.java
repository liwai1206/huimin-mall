package com.atguigu.gulimall.order.web;


import com.alipay.api.AlipayApiException;
import com.atguigu.gulimall.order.config.AlipayTemplate;
import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.vo.PayVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class PayWebController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private AlipayTemplate alipayTemplate;


    @ResponseBody
    @GetMapping(value = "/aliPayOrder", produces = MediaType.TEXT_HTML_VALUE)
    public String aliPayOrder(@RequestParam("orderSn") String orderSn) throws AlipayApiException {

        PayVo payVo = orderService.getOrderPay( orderSn );
        String pay = alipayTemplate.pay(payVo);
        return pay;
    }

}
