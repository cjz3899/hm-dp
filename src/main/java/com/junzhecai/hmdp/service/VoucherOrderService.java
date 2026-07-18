package com.junzhecai.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.VoucherOrder;

public interface VoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher(Long voucherId);

    Result createVoucherOrder(Long voucherId);
}
