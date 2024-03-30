package com.atguigu.gulimall.cart.service;

import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;

import java.util.List;
import java.util.concurrent.ExecutionException;

public interface CartService {
    CartItemVo addCartItem(Long skuId, int num) throws ExecutionException, InterruptedException;

    CartItemVo getCartItem(Long skuId);

    CartVo getCart();

    void checkItem(Long skuId, int check);

    void changeCount(Long skuId, int num);

    void deleteCartItem(Long skuId);

    List<CartItemVo> getUserCartItems();

}
