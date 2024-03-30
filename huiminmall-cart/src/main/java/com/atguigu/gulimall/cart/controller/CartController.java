package com.atguigu.gulimall.cart.controller;


import com.atguigu.common.utils.R;
import com.atguigu.gulimall.cart.intercepter.CartIntercepter;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.to.UserInfoTo;
import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Controller
public class CartController {

    @Autowired
    private CartService cartService;


    @ResponseBody
    @GetMapping("/currentUserCartItems")
    public List<CartItemVo> getCurrentUserItems(){
        return cartService.getUserCartItems();
    }

    /**
     * 去购物车页面的请求
     * 浏览器有一个cookie:user-key 标识用户的身份，一个月过期
     * 如果第一次使用jd的购物车功能，都会给一个临时的用户身份:
     * 浏览器以后保存，每次访问都会带上这个cookie；
     *
     * 登录：session有
     * 没登录：按照cookie里面带来user-key来做
     * 第一次，如果没有临时用户，自动创建一个临时用户
     *
     * @return
     */
    @GetMapping("/cart.html")
    public String cartListPage( Model model){

        // 得到用户信息
//        UserInfoTo userInfoTo = CartIntercepter.threadLocal.get();
        CartVo cartVo = cartService.getCart();
        model.addAttribute("cart", cartVo);

        return "cartList";
    }


    /**
     * 添加商品到购物车
     * @param skuId
     * @param num
     * @param redirectAttributes
     * @return
     */
    @GetMapping("/addCartItem")
    public String addCartItem(@RequestParam("skuId") Long skuId,
                              @RequestParam("num") int num,
                              RedirectAttributes redirectAttributes) throws ExecutionException, InterruptedException {

        cartService.addCartItem(skuId, num);
        redirectAttributes.addAttribute("skuId", skuId);
        return "redirect:http://cart.gulimall.com/addCartItemSuccess";
    }


    /**
     * 查询skuId对应的购物项
     * @param skuId
     * @param model
     * @return
     */
    @GetMapping("/addCartItemSuccess")
    public String addCartItemSuccess( @RequestParam("skuId") Long skuId, Model model ){
        CartItemVo cartItemVo = cartService.getCartItem( skuId );
        model.addAttribute("item", cartItemVo);
        return "success";
    }


    /**
     * 更新购物项的选中状态
     * @param skuId
     * @param check
     * @return
     */
    @GetMapping("/checkItem")
    public String checkItem( @RequestParam("skuId") Long skuId,
                        @RequestParam("check") int check){

        cartService.checkItem(skuId, check);
        return "redirect:http://cart.gulimall.com/cart.html";
    }


    /**
     * 更新购物项数量
     * @param skuId
     * @param num
     * @return
     */
    @GetMapping("/changeCount")
    public String changeCount( @RequestParam("skuId") Long skuId,
                          @RequestParam("num") int num){

        cartService.changeCount( skuId, num );
        return "redirect:http://cart.gulimall.com/cart.html";
    }


    /**
     * 删除购物项
     * @param skuId
     * @return
     */
    @GetMapping("/deleteCartItem")
    public String deleteCartItem( @RequestParam("skuId") Long skuId){
        cartService.deleteCartItem( skuId );
        return "redirect:http://cart.gulimall.com/cart.html";
    }

}
