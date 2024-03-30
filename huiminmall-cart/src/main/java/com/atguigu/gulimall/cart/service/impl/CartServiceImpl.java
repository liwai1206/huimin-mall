package com.atguigu.gulimall.cart.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.CartConstant;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.cart.feign.ProductFeignService;
import com.atguigu.gulimall.cart.intercepter.CartIntercepter;
import com.atguigu.gulimall.cart.service.CartService;
import com.atguigu.gulimall.cart.to.UserInfoTo;
import com.atguigu.gulimall.cart.vo.CartItemVo;
import com.atguigu.gulimall.cart.vo.CartVo;
import com.atguigu.gulimall.cart.vo.SkuInfoVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.BoundHashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ProductFeignService productFeignService;

    @Autowired
    private ThreadPoolExecutor executor;

    @Override
    public CartItemVo addCartItem(Long skuId, int num) throws ExecutionException, InterruptedException {

        BoundHashOperations<String, Object, Object> operations = getCartOps();

        String productRedisValue = (String) operations.get(skuId.toString());
        if (!StringUtils.isEmpty(productRedisValue)){
            // 如果redis中已经存在了，则更新数量
            CartItemVo cartItemVo = JSON.parseObject(productRedisValue, new TypeReference<CartItemVo>() {
            });

            cartItemVo.setCount(cartItemVo.getCount() + num);
            operations.put(skuId.toString(), JSON.toJSONString( cartItemVo ));
            return cartItemVo;
        }else {
            // 如果不存在，则添加
            CartItemVo cartItemVo = new CartItemVo();

            // 1.远程获取skuId对应的商品信息
            CompletableFuture<Void> spuInfoTask = CompletableFuture.runAsync(() -> {
                R info = productFeignService.info(skuId);
                SkuInfoVo data = info.getData("skuInfo", new TypeReference<SkuInfoVo>() {
                });
                cartItemVo.setSkuId(skuId);
                cartItemVo.setCheck(true);
                cartItemVo.setCount(num);
                cartItemVo.setImage(data.getSkuDefaultImg());
                cartItemVo.setPrice(data.getPrice());
                cartItemVo.setTitle(data.getSkuTitle());
            }, executor);


            // 2.远程获取该商品的销售属性信息
            CompletableFuture<Void> skuSaleAttrTask = CompletableFuture.runAsync(() -> {
                List<String> skuSaleAttrValues = productFeignService.getSkuSaleAttrValues(skuId);
                cartItemVo.setSkuAttrValues(skuSaleAttrValues);
            }, executor);

            // 3.添加到redis中
            CompletableFuture.allOf( spuInfoTask, skuSaleAttrTask ).get();
            operations.put( skuId.toString(), JSON.toJSONString( cartItemVo ));

            return cartItemVo;
        }
    }

    @Override
    public CartItemVo getCartItem(Long skuId) {

        BoundHashOperations<String, Object, Object> operations = getCartOps();
        String s = (String) operations.get(skuId.toString());
        CartItemVo cartItemVo = JSON.parseObject( s, CartItemVo.class);

        return cartItemVo;
    }


    /**
     * 查询用户购物车信息
     * @return
     */
    @Override
    public CartVo getCart() {
        UserInfoTo userInfoTo = CartIntercepter.threadLocal.get();

        CartVo cartVo = new CartVo();
        if ( userInfoTo.getUserId() != null ){
            // 登录了
            // 1.查询临时用户购物车
            List<CartItemVo> tempCartItemList = getCartItemList( CartConstant.CART_PREFIX + userInfoTo.getUserKey() );

            // 2.添加到登录用户购物车
            if ( tempCartItemList != null && tempCartItemList.size() > 0 ){
                tempCartItemList.forEach( item-> {
                    try {
                        addCartItem( item.getSkuId(), item.getCount() );
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }

            // 3.查询登录用户的购物车
            String cartKey = CartConstant.CART_PREFIX + userInfoTo.getUserId();
            List<CartItemVo> cartItemList = getCartItemList(cartKey);
            cartVo.setItems( cartItemList );

            // 4.删除临时用户购物车
            String tempCartKey = CartConstant.CART_PREFIX + userInfoTo.getUserKey();
            redisTemplate.delete( tempCartKey );
        }else {
            // 没登陆， 直接返回临时用户的购物车
            String cartKey = CartConstant.CART_PREFIX + userInfoTo.getUserKey();
            List<CartItemVo> cartItemList = getCartItemList(cartKey);
            cartVo.setItems( cartItemList );
        }

        return cartVo;
    }


    /**
     * 购物项选中状态发生改变
     * @param skuId
     * @param check
     */
    @Override
    public void checkItem(Long skuId, int check) {
        CartItemVo cartItem = getCartItem(skuId);
        cartItem.setCheck( check == 1 );

        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.put( skuId.toString(), JSON.toJSONString(cartItem));
    }


    /**
     * 更新购物项数量
     * @param skuId
     * @param num
     */
    @Override
    public void changeCount(Long skuId, int num) {
        CartItemVo cartItem = getCartItem(skuId);
        cartItem.setCount( num );

        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.put( skuId.toString(), JSON.toJSONString( cartItem ));
    }


    /**
     * 删除购物项
     * @param skuId
     */
    @Override
    public void deleteCartItem(Long skuId) {
        BoundHashOperations<String, Object, Object> cartOps = getCartOps();
        cartOps.delete(skuId.toString());
    }


    /**
     * 获取当前登录用户购物车中所有选中的商品
     * @return
     */
    @Override
    public List<CartItemVo> getUserCartItems() {
        UserInfoTo userInfoTo = CartIntercepter.threadLocal.get();
        if ( userInfoTo.getUserId() != null ){
            List<CartItemVo> cartItemList = getCartItemList(CartConstant.CART_PREFIX + userInfoTo.getUserId());

            List<CartItemVo> collect = cartItemList.stream().filter(item -> item.getCheck()).map(item -> {
                // 更新价格
                BigDecimal price = productFeignService.getPrice(item.getSkuId());
                item.setPrice( price );
                return item;
            }).collect(Collectors.toList());

            return collect;
        }
        return null;
    }

    private List<CartItemVo> getCartItemList(String cartKey) {
        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(cartKey);
        List<CartItemVo> collect = operations.values().stream().map(item -> {
            String s = (String) item;
            CartItemVo cartItemVo = JSON.parseObject(s, CartItemVo.class);
            return cartItemVo;
        }).collect(Collectors.toList());
        return collect;
    }


    /**
     * 获取到我们要操作的购物车 -- redis
     * @return
     */
    private  BoundHashOperations<String, Object, Object> getCartOps() {
        UserInfoTo userInfoTo = CartIntercepter.threadLocal.get();
        String cartKey = "";
        if ( userInfoTo.getUserId() != null ){
            // 已经登录了
            cartKey = CartConstant.CART_PREFIX + userInfoTo.getUserId();
        }else {
            cartKey = CartConstant.CART_PREFIX + userInfoTo.getUserKey();
        }

        BoundHashOperations<String, Object, Object> operations = redisTemplate.boundHashOps(cartKey);
        return operations;
    }
}
