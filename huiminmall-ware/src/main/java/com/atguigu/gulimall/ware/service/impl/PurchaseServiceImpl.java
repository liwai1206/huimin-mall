package com.atguigu.gulimall.ware.service.impl;

import com.atguigu.gulimall.ware.service.WareSkuService;
import com.atguigu.gulimall.ware.vo.MergeVo;
import com.atguigu.gulimall.ware.entity.PurchaseDetailEntity;
import com.atguigu.gulimall.ware.service.PurchaseDetailService;
import com.atguigu.gulimall.ware.vo.PurchaseDoneVo;
import com.atguigu.gulimall.ware.vo.PurchaseItemDoneVo;
import com.xunqi.common.constant.WareConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.atguigu.common.utils.PageUtils;
import com.atguigu.common.utils.Query;

import com.atguigu.gulimall.ware.dao.PurchaseDao;
import com.atguigu.gulimall.ware.entity.PurchaseEntity;
import com.atguigu.gulimall.ware.service.PurchaseService;
import org.springframework.transaction.annotation.Transactional;


@Service("purchaseService")
public class PurchaseServiceImpl extends ServiceImpl<PurchaseDao, PurchaseEntity> implements PurchaseService {
    @Autowired
    private PurchaseDetailService purchaseDetailService;

    @Autowired
    private WareSkuService wareSkuService;

    @Override
    public PageUtils queryPage(Map<String, Object> params) {
        IPage<PurchaseEntity> page = this.page(
                new Query<PurchaseEntity>().getPage(params),
                new QueryWrapper<PurchaseEntity>()
        );

        return new PageUtils(page);
    }


    /**
     * 查询未领取的采购单
     * @param params
     * @return
     */
    @Override
    public PageUtils queryUnreceivePage(Map<String, Object> params) {
        IPage<PurchaseEntity> page = this.page(
                new Query<PurchaseEntity>().getPage(params),
                new QueryWrapper<PurchaseEntity>().eq("status",0).or().eq("status",1)
        );

        return new PageUtils(page);
    }

    @Override
    public void merge(MergeVo mergeVo) {
        Long purchaseId = mergeVo.getPurchaseId();
        if ( purchaseId == null ){
            // 创建采购单
            PurchaseEntity purchaseEntity = new PurchaseEntity();
            purchaseEntity.setUpdateTime( new Date());
            purchaseEntity.setCreateTime(new Date());
            purchaseEntity.setStatus(WareConstant.PurchaseStatusEnum.CREATED.getCode());

            this.save( purchaseEntity);
            purchaseId = purchaseEntity.getId();
        }

        // 合并
        // 更新所有需要更新的PurchaseDetailEntity
        List<Long> items = mergeVo.getItems();
        Long finalPurchaseId = purchaseId;
        items.forEach(i -> {

            // 先判断该采购需求是否已经分配给采购单了
            Integer status = purchaseDetailService.getById(i).getStatus();
            if ( status == WareConstant.PurchaseDetailStatusEnum.CREATED.getCode() || status == WareConstant.PurchaseDetailStatusEnum.ASSIGNED.getCode() ){
                PurchaseDetailEntity purchaseDetailEntity = new PurchaseDetailEntity();
                purchaseDetailEntity.setPurchaseId(finalPurchaseId);
                purchaseDetailEntity.setId( i );
                purchaseDetailEntity.setStatus( WareConstant.PurchaseDetailStatusEnum.ASSIGNED.getCode());

                purchaseDetailService.updateById( purchaseDetailEntity );

                // 最后还需修改采购单的修改时间
                PurchaseEntity entity = new PurchaseEntity();
                entity.setUpdateTime( new Date());
                entity.setId( finalPurchaseId );
                this.updateById( entity );
            }
        });
    }

    /**
     * 领取采购单
     * @param ids
     */
    @Override
    public void received(List<Long> ids) {
        // 1、首先判断采购单的状态只能为创建和已分配的
        List<PurchaseEntity> collect = ids.stream().map(id -> {
            PurchaseEntity purchaseEntity = this.getById(id);
            return purchaseEntity;
        }).filter(item -> {
            return item.getStatus() == WareConstant.PurchaseStatusEnum.CREATED.getCode() ||
                    item.getStatus() == WareConstant.PurchaseStatusEnum.ASSIGNED.getCode();
        }).map( item -> {
            item.setStatus( WareConstant.PurchaseStatusEnum.RECEIVE.getCode() );
            item.setUpdateTime( new Date());
            return item;
        }).collect(Collectors.toList());

        // 2、更新采购单的状态为已领取
        this.updateBatchById( collect );

        // 3、设置采购需求的状态为正在购买
        ids.forEach( id -> {
            // 查询该采购单的所有采购需求
            List<PurchaseDetailEntity> purchaseDetailEntities = purchaseDetailService.listPurchaseDetailByPurchaseId( id );

            purchaseDetailEntities.forEach( item -> {
                 item.setStatus( WareConstant.PurchaseDetailStatusEnum.BUYING.getCode());
            });
            purchaseDetailService.updateBatchById( purchaseDetailEntities );
        });
    }


    /**
     * 完成采购
     * @param purchaseDoneVo
     */
    @Transactional
    @Override
    public void done(PurchaseDoneVo purchaseDoneVo) {

        Long id = purchaseDoneVo.getId();

        // 1.更新采购项的状态
        AtomicBoolean flag = new AtomicBoolean(true);
        List<PurchaseItemDoneVo> items = purchaseDoneVo.getItems();
        items.forEach( item -> {
            PurchaseDetailEntity purchaseDetailEntity = new PurchaseDetailEntity();
            purchaseDetailEntity.setId( item.getItemId() );
            purchaseDetailEntity.setStatus( item.getStatus() );

            purchaseDetailService.updateById( purchaseDetailEntity );

            if ( item.getStatus() == WareConstant.PurchaseDetailStatusEnum.HASERROR.getCode() ){
                // 采购失败
                flag.set(false);
            }else {
                // 采购成功，将成功的商品入库
                PurchaseDetailEntity detailEntity = purchaseDetailService.getById(item.getItemId());
                wareSkuService.addStock( detailEntity.getSkuId(), detailEntity.getWareId(), detailEntity.getSkuNum());
            }

        });

        // 2.更新采购单的状态
        PurchaseEntity purchaseEntity = new PurchaseEntity();
        purchaseEntity.setId( id );
        purchaseEntity.setStatus( flag.get()? WareConstant.PurchaseStatusEnum.FINISH.getCode() : WareConstant.PurchaseStatusEnum.HASERROR.getCode());
        purchaseEntity.setUpdateTime( new Date());
        baseMapper.updateById( purchaseEntity );

    }

}