package com.junzhecai.hmdp.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.VoucherMapper;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.SeckillVoucher;
import com.junzhecai.hmdp.model.entity.Voucher;
import com.junzhecai.hmdp.service.SeckillVoucherService;
import com.junzhecai.hmdp.service.VoucherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Voucher> implements VoucherService {
    @Autowired
    private SeckillVoucherService seckillVoucherService;

    @Override
    @Transactional//事务管理，确保数据的一致性
    public void addSeckillVoucher(Voucher voucher) {
        // 保存优惠券
        save(voucher);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(voucher.getId());
        seckillVoucher.setStock(voucher.getStock());
        seckillVoucher.setBeginTime(voucher.getBeginTime());
        seckillVoucher.setEndTime(voucher.getEndTime());
        seckillVoucherService.save(seckillVoucher);
    }

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券
        List<Voucher> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        return Result.ok(vouchers);
    }
}
