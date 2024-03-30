package com.atguigu.gulimall.order.web;


import com.atguigu.common.exception.NoStockException;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.order.service.OrderService;
import com.atguigu.gulimall.order.vo.OrderConfirmVo;
import com.atguigu.gulimall.order.vo.OrderSubmitVo;
import com.atguigu.gulimall.order.vo.SubmitOrderResponseVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Controller
public class OrderWebController {

    @Autowired
    public OrderService orderService;


    @GetMapping("/toTrade")
    public String toTrade(Model model, @RequestParam(value = "msg", required = false) String msg) throws ExecutionException, InterruptedException {
        OrderConfirmVo confirmVo = orderService.confirmOrder();
        model.addAttribute("orderConfirmData", confirmVo);

        if ( !StringUtils.isEmpty( msg )){
            model.addAttribute("msg", msg);
        }

        return "confirm";
    }


    @PostMapping("/submitOrder")
    public String submitOrder(OrderSubmitVo orderSubmitVo, Model model, RedirectAttributes redirectAttributes){
//        System.out.println( orderSubmitVo );
        try {
            SubmitOrderResponseVo responseVo = orderService.submitOrder( orderSubmitVo );

            if ( responseVo.getCode() == 0 ){
                // 成功，跳转到pay页面
                model.addAttribute("submitOrderResp", responseVo);
                return "pay";
            }else {
                String msg = "下单失败";
                switch ( responseVo.getCode() ){
                    case 1: msg+="令牌订单信息过期，请刷新再次提交";break;
                    case 2: msg+="订单商品价格发生变化，请确认后再次提交";break;
                    case 3: msg+="库存锁定失败，商品库存不足";break;
                }
                redirectAttributes.addAttribute("msg", msg);
                return "redirect:http://order.gulimall.com/toTrade";
            }
        } catch (Exception e) {
            if ( e instanceof NoStockException) {
                String message = e.getMessage();
                redirectAttributes.addAttribute("msg", message);
            }
            return "redirect:http://order.gulimall.com/toTrade";
        }


    }

}
