package com.junzhecai.hmdp.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.VoucherOrderMapper;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.VoucherOrder;
import com.junzhecai.hmdp.service.SeckillVoucherService;
import com.junzhecai.hmdp.service.VoucherOrderService;
import com.junzhecai.hmdp.utils.RedisIdWorker;
import com.junzhecai.hmdp.utils.UserHolder;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements VoucherOrderService {
    @Autowired
    private SeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWorker redisIdWorker;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    // 阻塞队列：存放待异步处理的秒杀订单
    private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

    // 单线程线程池：从阻塞队列中取出订单并写入数据库
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 自注入代理对象（@Lazy 避免循环依赖）：用于调用 @Transactional 方法，解决内部调用事务失效问题
    @Lazy
    @Autowired
    private VoucherOrderService proxy;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 初始化：Spring Bean 创建完成后启动异步消费者线程
     */
    @PostConstruct
    private void init() {
        // 启动单线程消费者，不断从阻塞队列中取出订单并写入数据库
        SECKILL_ORDER_EXECUTOR.submit(() -> {
            while (true) {
                try {
                    // take() 阻塞等待，直到队列中有新订单
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 处理订单：加分布式锁 + 写入数据库
                    handlerVoucherOrder(voucherOrder);
                } catch (InterruptedException e) {
                    // 线程被中断（通常是关闭线程池时），记录日志后退出循环
                    log.error("秒杀订单消费者线程被中断", e);
                    return;
                } catch (Exception e) {
                    // 其他异常只记日志，不退出循环，保证消费者持续存活
                    log.error("处理秒杀订单异常", e);
                }
            }
        });
    }

    /**
     * 处理单个秒杀订单：加用户级分布式锁后落库，防止重复下单
     */
    private void handlerVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        // 基于 Redisson 的分布式锁，锁粒度为用户级别
        // 同一用户同时只能处理一笔订单，避免数据库层面的重复写入
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        if (!isLock) {
            // 获取锁失败，说明该用户有其他订单正在处理，当前订单丢弃
            log.error("用户 {} 的订单正在处理中，不允许重复下单", userId);
            return;
        }
        try {
            // 通过代理对象调用，使 @Transactional 生效
            proxy.createVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }

    // ======================== Redis 优化秒杀（Lua 脚本 + 异步落库） ========================

    /**
     * 秒杀优惠券主流程：
     * 1. 执行 Lua 脚本，原子化校验库存 + 一人一单 + 扣减库存
     * 2. 脚本返回 0 表示抢购成功，将订单放入阻塞队列后立即返回
     * 3. 异步消费者线程负责将队列中的订单写入数据库
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1. 执行 Lua 脚本，Redis 原子化操作：校验库存、校验一人一单、扣减库存
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );
        int r = result.intValue();
        if (r != 0) {
            // r == 1: 库存不足  r == 2: 用户已购买过
            return Result.fail(r == 1 ? "库存不足" : "用户已经购买过");
        }

        // 2. 生成全局唯一订单 ID（雪花算法）
        long orderId = redisIdWorker.nextId("order");

        // 3. 构建订单对象，放入阻塞队列（后续由异步线程落库）
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);

        // 4. 立即返回订单 ID，不阻塞用户请求
        return Result.ok(orderId);
    }

    // ======================== 异步落库 ========================

    /**
     * 将已校验通过的订单写入数据库（由异步消费者线程调用）
     * 注意：此方法通过 proxy 调用以触发 Spring AOP 事务管理
     * 参数 VoucherOrder 中的 userId 和 voucherId 已在主线程设置完毕，
     * 异步线程中不再依赖 ThreadLocal（UserHolder）
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long userId = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();

        // 1. 一人一单校验（数据库层面兜底，防止极端情况下 Redis 与 DB 不一致）
        Long count = query()
                .eq("user_id", userId)
                .eq("voucher_id", voucherId)
                .count();
        if (count > 0) {
            log.error("用户 {} 已经购买过优惠券 {}", userId, voucherId);
            return;
        }

        // 2. 扣减库存（乐观锁，stock > 0 防止超卖）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // 乐观锁：库存必须大于 0 才允许扣减
                .update();
        if (!success) {
            log.error("优惠券 {} 库存不足", voucherId);
            return;
        }

        // 3. 保存订单到数据库
        save(voucherOrder);
    }

    /**
     * 销毁前关闭线程池，确保应用优雅停机
     */
    @PreDestroy
    private void destroy() {
        SECKILL_ORDER_EXECUTOR.shutdown();
    }
    /*@Override
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
        //SimpleRedisLock lock = new SimpleRedisLock("lock:order:" + userId, stringRedisTemplate);
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
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
        *//*锁必须包住整个下单流程，不能只放 createVoucherOrder 里面
        打个比方：如果不把 "查库存 + 扣库存 + 查订单 + 建订单" 用同一把锁串起来，
        就会出现两个线程先后查到 "还有1件"，都冲进去每人抢一件，结果库存变 - 1，一人买了两单
        intern() 保证同一 userId 的字符串是同一个对象，锁才锁得住*//*
     *//*synchronized (userId.toString().intern()) {
            // 获取代理对象
            VoucherOrderService proxy = (VoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*//*
    }*/


    /*@Transactional
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
    }*/
}
