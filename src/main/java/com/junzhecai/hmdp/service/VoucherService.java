package com.junzhecai.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.Voucher;

public interface VoucherService extends IService<Voucher> {


    void addSeckillVoucher(Voucher voucher);

    Result queryVoucherOfShop(Long shopId);
}
