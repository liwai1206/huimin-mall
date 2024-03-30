package com.atguigu.gulimall.product.service.impl;

import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.ProductConstant;
import com.atguigu.common.es.SkuEsModel;
import com.atguigu.common.to.SkuReductionTo;
import com.atguigu.common.to.SpuBoundTo;
import com.atguigu.common.utils.R;
import com.atguigu.gulimall.product.entity.*;
import com.atguigu.gulimall.product.feign.CouponFeignService;
import com.atguigu.gulimall.product.feign.SearchFeignService;
import com.atguigu.gulimall.product.feign.WareFeignService;
import com.atguigu.gulimall.product.service.*;
import com.atguigu.gulimall.product.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.product.dao.SpuInfoDao;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;


@Service("spuInfoService")
@Slf4j
public class SpuInfoServiceImpl extends ServiceImpl<SpuInfoDao, SpuInfoEntity> implements SpuInfoService {

    @Autowired
    private SpuInfoDescService spuInfoDescService;

    @Autowired
    private SpuImagesService spuImagesService;

    @Autowired
    private AttrService attrService;

    @Autowired
    private ProductAttrValueService productAttrValueService;

    @Autowired
    private CouponFeignService couponFeignService;

    @Autowired
    private SkuInfoService skuInfoService;

    @Autowired
    private SkuImagesService skuImagesService;

    @Autowired
    private SkuSaleAttrValueService skuSaleAttrValueService;

    @Autowired
    private WareFeignService wareFeignService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private SearchFeignService searchFeignService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                new QueryWrapper<SpuInfoEntity>()
        );

        return new PageUtils(page);
    }

    @Transactional
    @Override
    public void saveSpuInfo(SpuInfoSaveVo spuInfoSaveVo) {

        // 1.保存spu的基本信息
        SpuInfoEntity infoEntity = new SpuInfoEntity();
        BeanUtils.copyProperties( spuInfoSaveVo, infoEntity);
        infoEntity.setCreateTime( new Date());
        infoEntity.setUpdateTime( new Date());
        this.save( infoEntity );

        // 2. 保存spu的描述图片
        List<String> decript = spuInfoSaveVo.getDecript();
        SpuInfoDescEntity descEntity = new SpuInfoDescEntity();
        descEntity.setSpuId( infoEntity.getId() );
        descEntity.setDecript( String.join(",", decript));
        spuInfoDescService.save( descEntity );

        // 3. 保存spu的图片集
        List<String> images = spuInfoSaveVo.getImages();
        spuImagesService.saveImages( infoEntity.getId(), images );

        // 4. 保存spu的规格参数
        List<BaseAttrs> baseAttrs = spuInfoSaveVo.getBaseAttrs();
        List<ProductAttrValueEntity> collect = baseAttrs.stream().map(attr -> {
            ProductAttrValueEntity valueEntity = new ProductAttrValueEntity();
            valueEntity.setSpuId(infoEntity.getId());
            valueEntity.setAttrValue(attr.getAttrValues());
            valueEntity.setAttrId(attr.getAttrId());

            AttrEntity entity = attrService.getById(attr.getAttrId());
            valueEntity.setAttrName(entity.getAttrName());
            valueEntity.setQuickShow(attr.getShowDesc());
            return valueEntity;
        }).collect(Collectors.toList());
        productAttrValueService.saveProductAttr( collect );

        // 5. 保存当前spu对于的所有sku信息
        Bounds bounds = spuInfoSaveVo.getBounds();
        SpuBoundTo spuBoundTo = new SpuBoundTo();
        BeanUtils.copyProperties( bounds, spuBoundTo );
        spuBoundTo.setSpuId( infoEntity.getId() );
        R r = couponFeignService.saveSpuBounds(spuBoundTo);
        if ( r.getCode() != 0  ){
            log.error("远程保存spu积分信息失败");
        }

        // 6. 保存当前spu对应的所有sku信息
        List<Skus> skus = spuInfoSaveVo.getSkus();
        if ( skus != null && skus.size() > 0 ){
            skus.forEach( item -> {
                String defaultImg = "";

                for (Images image : item.getImages()) {
                    if ( image.getDefaultImg() == 1 ){
                        defaultImg = image.getImgUrl();
                    }
                }

                SkuInfoEntity skuInfoEntity = new SkuInfoEntity();
                BeanUtils.copyProperties( item, skuInfoEntity);
                skuInfoEntity.setBrandId( infoEntity.getBrandId() );
                skuInfoEntity.setCatalogId( infoEntity.getCatalogId() );
                skuInfoEntity.setSaleCount( 0l );
                skuInfoEntity.setSpuId( infoEntity.getId() );
                skuInfoEntity.setSkuDefaultImg( defaultImg );

                // 6.1 sku的基本信息
                skuInfoService.saveSkuInfo(skuInfoEntity);

                Long skuId = skuInfoEntity.getSkuId();

                List<SkuImagesEntity> imagesEntities = item.getImages().stream().map(img -> {
                    SkuImagesEntity skuImagesEntity = new SkuImagesEntity();
                    skuImagesEntity.setSkuId(skuId);
                    skuImagesEntity.setImgUrl(img.getImgUrl());
                    skuImagesEntity.setDefaultImg(img.getDefaultImg());
                    return skuImagesEntity;
                }).filter(entity -> {
                    return !StringUtils.isEmpty(entity.getImgUrl());
                }).collect(Collectors.toList());

                // 6.2 sku的图片信息，没有图片路径的无需保存，通过filter已经过滤了
                skuImagesService.saveBatch( imagesEntities );

                List<Attr> attr = item.getAttr();
                List<SkuSaleAttrValueEntity> skuSaleAttrValueEntities = attr.stream().map(a -> {
                    SkuSaleAttrValueEntity attrValueEntity = new SkuSaleAttrValueEntity();
                    BeanUtils.copyProperties(a, attrValueEntity);
                    attrValueEntity.setSkuId(skuId);

                    return attrValueEntity;
                }).collect(Collectors.toList());

                // 6.3 sku的销售属性信息
                skuSaleAttrValueService.saveBatch( skuSaleAttrValueEntities );

                // 6.4 sku的优惠、满减等信息
                SkuReductionTo skuReductionTo = new SkuReductionTo();
                BeanUtils.copyProperties( item, skuReductionTo );
                skuReductionTo.setSkuId( skuId );
                if ( skuReductionTo.getFullCount() > 0 || skuReductionTo.getFullPrice().compareTo( new BigDecimal("0")) == 1){
                    R r1 = couponFeignService.saveSkuReduction(skuReductionTo);
                    if ( r1.getCode() != 0 ){
                        log.error("远程保存sku优惠信息失败");
                    }
                }

            });
        }

    }

    @Override
    public PageUtils queryPageCondition(Map<String, Object> params) {
        QueryWrapper<SpuInfoEntity> wrapper = new QueryWrapper<>();

        String key = (String) params.get("key");
        if ( !StringUtils.isEmpty( key )){
            wrapper.and( w -> {
                w.eq("id", key).or().like("spu_name", key);
            });
        }

        String brandId = (String) params.get("brandId");
        if (!StringUtils.isEmpty( brandId ) ){
            wrapper.eq("brand_id", brandId);
        }

        String catelogId = (String) params.get("catelogId");
        if (!StringUtils.isEmpty( brandId ) ){
            wrapper.eq("catalog_id", catelogId);
        }

        String status = (String) params.get("status");
        if (!StringUtils.isEmpty( status ) ){
            wrapper.eq("publish_status", status);
        }

        IPage<SpuInfoEntity> page = this.page(
                new Query<SpuInfoEntity>().getPage(params),
                wrapper
        );

        return new PageUtils(page);
    }

    /**
     * 商品上架
     * @param spuId
     */
    @Override
    public void up(Long spuId) {
        // 1.查出当前spuId对应的所有sku信息，品牌的名字
        List<SkuInfoEntity> skuInfoEntities = skuInfoService.getSkusBySpuId( spuId );

        // 查出当前sku的所有可以被用来检索的规格属性
        List<ProductAttrValueEntity> baseAttrs = productAttrValueService.listForSpu(spuId);

        List<Long> attrIds = baseAttrs.stream().map(attr -> {
            return attr.getAttrId();
        }).collect(Collectors.toList());

        List<Long> searchAttrIds = attrService.selectSearchAttrs( attrIds );
        // 转换为set集合
        HashSet<Long> idSet = new HashSet<>(searchAttrIds);

        List<SkuEsModel.Attrs> attrsList = baseAttrs.stream().filter(item -> {
            return idSet.contains(item.getAttrId());
        }).map(item -> {
            SkuEsModel.Attrs attrs = new SkuEsModel.Attrs();
            BeanUtils.copyProperties(item, attrs);
            return attrs;
        }).collect(Collectors.toList());

        // 获取所有的skuId
        List<Long> skuIdList = skuInfoEntities.stream().map(SkuInfoEntity::getSkuId).collect(Collectors.toList());

        // 发送远程调用，库存系统查询是否有库存
        Map<Long, Boolean> stockMap = null;

        try {
            R skuHasStock = wareFeignService.getSkuHasStock(skuIdList);
            TypeReference<List<SkuHasStockVo>> typeReference = new TypeReference<List<SkuHasStockVo>>() {
            };
            stockMap = skuHasStock.getData(typeReference).stream().collect(Collectors.toMap(SkuHasStockVo::getSkuId, SkuHasStockVo::getHasStock));
        } catch (Exception e) {
            e.printStackTrace();
            log.error("库存服务查询异常：原因{}",e);
        }

        // 封装每个sku的信息
        Map<Long, Boolean> finalStockMap = stockMap;
        List<SkuEsModel> collect = skuInfoEntities.stream().map(sku -> {
            // 组装需要的数据
            SkuEsModel esModel = new SkuEsModel();
            esModel.setSkuPrice(sku.getPrice());
            esModel.setSkuImg(sku.getSkuDefaultImg());

            // 设置库存信息
            if (finalStockMap == null) {
                esModel.setHasStock(true);
            } else {
                esModel.setHasStock(finalStockMap.get(sku.getSkuId()));
            }

            // todo： 热度评分
            esModel.setHotScore(0l);

            // 查询品牌和分类的名字信息
            BrandEntity brandEntity = brandService.getById(sku.getBrandId());
            esModel.setBrandId(brandEntity.getBrandId());
            esModel.setBrandName(brandEntity.getName());
            esModel.setBrandImg(brandEntity.getLogo());

            CategoryEntity categoryEntity = categoryService.getById(sku.getCatalogId());
            esModel.setCatalogId(categoryEntity.getCatId());
            esModel.setCatalogName(categoryEntity.getName());

            // 设置检索属性
            esModel.setAttrs(attrsList);

            BeanUtils.copyProperties(sku, esModel);
            return esModel;
        }).collect(Collectors.toList());

        // 最后将数据发给es进行保存
        R r = searchFeignService.productStatusUp(collect);
        if ( r.getCode() == 0 ){
            // 成功,修改当前spu的状态
            baseMapper.updateSpuStatus( spuId, ProductConstant.ProductStatusEnum.SPU_UP.getCode());
        }else {
            //远程调用失败
            //TODO 7、重复调用？接口幂等性:重试机制
        }


    }

    /**
     * 根据skuId查询spu的信息
     * @param skuId
     * @return
     */
    @Override
    public SpuInfoEntity getSpuInfoBySkuId(Long skuId) {
        //先查询sku表里的数据
        SkuInfoEntity skuInfoEntity = skuInfoService.getById(skuId);

        //获得spuId
        Long spuId = skuInfoEntity.getSpuId();

        //再通过spuId查询spuInfo信息表里的数据
        SpuInfoEntity spuInfoEntity = this.baseMapper.selectById(spuId);

        //查询品牌表的数据获取品牌名
        BrandEntity brandEntity = brandService.getById(spuInfoEntity.getBrandId());
        spuInfoEntity.setBrandName(brandEntity.getName());

        return spuInfoEntity;
    }

}