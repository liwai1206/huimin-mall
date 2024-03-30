package com.atguigu.gulimall.product.entity;

import com.atguigu.common.valid.AddGroup;
import com.atguigu.common.valid.ListValue;
import com.atguigu.common.valid.UpdateGroup;
import com.atguigu.common.valid.UpdateSatusGroup;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.util.Date;
import lombok.Data;

import javax.validation.constraints.*;

/**
 * Ʒ?
 * 
 * @author 22wli
 * @email 22wli@gmail.com
 * @date 2023-10-14 13:42:17
 */
@Data
@TableName("pms_brand")
public class BrandEntity implements Serializable {
	private static final long serialVersionUID = 1L;

	/**
	 * Ʒ??id
	 */
	@TableId
	@NotNull(message = "修改必须指定品牌id",groups = {UpdateGroup.class, UpdateSatusGroup.class})
	@Null(message = "新增不能指定id",groups = {AddGroup.class})
	private Long brandId;
	/**
	 * Ʒ????
	 */
	@NotNull(message = "品牌名必须提交",groups = {AddGroup.class,UpdateGroup.class})
	private String name;
	/**
	 * Ʒ??logo??ַ
	 */
	@NotNull(message = "logo必须是一个合法的url地址",groups = {AddGroup.class,UpdateGroup.class})
	private String logo;
	/**
	 * ???
	 */
	private String descript;
	/**
	 * ??ʾ״̬[0-????ʾ??1-??ʾ]
	 */
	@NotNull(groups = {AddGroup.class, UpdateSatusGroup.class})
	@ListValue(vals = {0,1}, groups = {AddGroup.class,UpdateSatusGroup.class})
	private Integer showStatus;

	/**
	 * ????????ĸ
	 */
	@NotEmpty(groups = {AddGroup.class})
	@Pattern(regexp = "^[a-zA-Z]$",message = "检索首字母必须是一个字母", groups = {AddGroup.class,UpdateGroup.class})
	private String firstLetter;
	/**
	 * ???
	 */
	@NotNull(groups = {AddGroup.class})
	@Min(value = 0,message = "排序必须大于等于0", groups = {AddGroup.class,UpdateGroup.class})
	private Integer sort;

}
