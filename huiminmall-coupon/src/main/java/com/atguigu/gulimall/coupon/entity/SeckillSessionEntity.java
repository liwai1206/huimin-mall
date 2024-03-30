package com.atguigu.gulimall.coupon.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

import lombok.Data;

/**
 * ??ɱ????
 * 
 * @author 22wli
 * @email 22wli@gmail.com
 * @date 2023-10-14 15:28:01
 */
@Data
@TableName("sms_seckill_session")
public class SeckillSessionEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * id
	 */
	@TableId
	private Long id;
	/**
	 * ???????
	 */
	private String name;
	/**
	 * ÿ?տ?ʼʱ?
	 */
	private Date startTime;
	/**
	 * ÿ?ս???ʱ?
	 */
	private Date endTime;
	/**
	 * ????״̬
	 */
	private Integer status;
	/**
	 * ????ʱ?
	 */
	private Date createTime;

	@TableField(exist = false)
	private List<SeckillSkuRelationEntity> relationSkus;
}
