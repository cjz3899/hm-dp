package com.junzhecai.hmdp.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.VoucherOrderMapper;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.SeckillVoucher;
import com.junzhecai.hmdp.model.entity.VoucherOrder;
import com.junzhecai.hmdp.service.SeckillVoucherService;
import com.junzhecai.hmdp.service.VoucherOrderService;
import com.junzhecai.hmdp.utils.RedisIdWorker;
import com.junzhecai.hmdp.utils.SimpleRedisLock;
import com.junzhecai.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {
    @Autowired
    private SeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀券是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀尚未开始!");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束!");
        }
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }

        Long userId = UserHolder.getUser().getId();
        //基于Redis的分布式锁（解决集群环境下的重复下单问题）
        //创建锁对象
        SimpleRedisLock lock = new SimpleRedisLock("order" + userId, stringRedisTemplate);
        boolean isLock = lock.tryLock(1200);
        if (!isLock) {
            //获取锁失败
            return Result.fail("请勿重复下单");
        }
        try {
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
        /*锁必须包住整个下单流程，不能只放 createVoucherOrder 里面
        打个比方：如果不把 "查库存 + 扣库存 + 查订单 + 建订单" 用同一把锁串起来，
        就会出现两个线程先后查到 "还有1件"，都冲进去每人抢一件，结果库存变 - 1，一人买了两单
        intern() 保证同一 userId 的字符串是同一个对象，锁才锁得住*/
        /*synchronized (userId.toString().intern()) {
            // 获取代理对象
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*/
    }

    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            //用户已经下过订单
            return Result.fail("用户已经购买过一次!");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1").eq("voucher_id", voucherId)
                .gt("stock", 0)// 添加库存大于0的条件，防止超卖
                .update();
        if (!success) {
            return Result.fail("库存不足!");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //设置订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //设置用户id
        voucherOrder.setUserId(userId);
        //设置优惠券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        return Result.ok(orderId);
    }
}
