package com.atguigu.gulimall.product.service.impl;

import com.atguigu.gulimall.product.vo.SkuItemSaleAttrVo;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.SkuSaleAttrValueDao;
import com.atguigu.gulimall.product.entity.SkuSaleAttrValueEntity;
import com.atguigu.gulimall.product.service.SkuSaleAttrValueService;


@Service("skuSaleAttrValueService")
public class SkuSaleAttrValueServiceImpl extends ServiceImpl<SkuSaleAttrValueDao, SkuSaleAttrValueEntity> implements SkuSaleAttrValueService {

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SkuSaleAttrValueEntity> page = this.page(
                new Query<SkuSaleAttrValueEntity>().getPage(params),
                new QueryWrapper<SkuSaleAttrValueEntity>()
        );

        return new PageUtils(page);
    }


    /**
     * 获取spu的销售属性组合
     * @param spuId
     * @return
     */
    @Override
    public List<SkuItemSaleAttrVo> getSaleAttrBySpuId(Long spuId) {

        List<SkuItemSaleAttrVo> vos =  baseMapper.getSaleAttrBySpuId(spuId);

        return vos;
    }

    @Override
    public List<String> getSkuSaleAttrValues(Long skuId) {

        return  baseMapper.getSkuSaleAttrValues(skuId);
    }

}