package com.atguigu.gulimall.order.vo;


import lombok.Data;

import java.math.BigDecimal;

@Data
public class FareVo {

    // 收货地址详细信息
    private MemberAddressVo address;
    // 快递费用
    private BigDecimal fare;
}
