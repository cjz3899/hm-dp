package com.junzhecai.hmdp.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.VoucherOrderMapper;
import com.junzhecai.hmdp.model.entity.VoucherOrder;
import com.junzhecai.hmdp.service.VoucherOrderService;
import org.springframework.stereotype.Service;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {
}
