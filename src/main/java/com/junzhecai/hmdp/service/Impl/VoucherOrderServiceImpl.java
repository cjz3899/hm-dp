package com.junzhecai.hmdp.service.Impl;

import cn.hutool.core.bean.BeanUtil;
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
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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


    // 单线程线程池：从阻塞队列中取出订单并写入数据库
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    // 消费者线程运行标志：应用关闭时置为 false 以退出无限循环（不能依赖线程中断标志，Lettuce 会清除它）
    private volatile boolean consumerRunning = true;

    // 自注入代理对象（@Lazy 避免循环依赖）：用于调用 @Transactional 方法，解决内部调用事务失效问题
    @Lazy
    @Autowired
    private VoucherOrderService proxy;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    // 阻塞队列：存放待异步处理的秒杀订单
    /*private final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

     */

    /**
     * 初始化：Spring Bean 创建完成后启动异步消费者线程
     *//*
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
    }*/
    @PostConstruct
    private void init() {
        String queueName = "stream.orders";
        // 确保 Stream 与消费组存在，否则 XREADGROUP 会报 NOGROUP 错误导致消费者异常
        ensureStreamAndGroup(queueName);
        // 启动单线程消费者，不断从阻塞队列中取出订单并写入数据库
        SECKILL_ORDER_EXECUTOR.submit(() -> {
            while (consumerRunning) {
                try {
                    //获取消息队列中的订单信息 xreadgroup group g1 c1 count 1 block 2000 streams stream.orders
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );
                    //没有获取到消息就进入下一次循环
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    //解析消息中的订单信息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                    handlerVoucherOrder(voucherOrder);
                    //ack确认 sack stream.orders g1 id
                    stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
                } catch (Exception e) {
                    //应用关闭时连接工厂已被销毁，不再处理消息，直接退出
                    if (!consumerRunning) {
                        log.info("秒杀订单消费者线程已停止，退出循环");
                        return;
                    }
                    //其他异常只记日志，不退出循环，保证消费者持续存活
                    log.error("处理秒杀订单异常", e);
                    //处理未确认的消息列表
                    handlerPendingList(queueName);
                }
            }
        });
    }

    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        //生成全局唯一订单 ID（雪花算法）
        long orderId = redisIdWorker.nextId("order");
        //执行 Lua 脚本，Redis 原子化操作：校验库存、校验一人一单、扣减库存、记录用户、发送订单消息到 Stream 队列
        //返回值：0-成功  1-库存不足  2-用户已购买过
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)
        );
        int r = result.intValue();
        if (r != 0) {
            //r == 1: 库存不足  r == 2: 用户已购买过
            return Result.fail(r == 1 ? "库存不足" : "用户已经购买过");
        }

        //获取代理对象
        proxy = (VoucherOrderService) AopContext.currentProxy();
        //返回订单ID
        return Result.ok(orderId);
    }

    /**
     * 确保 Stream 与消费组存在（幂等），避免 XREADGROUP 报 NOGROUP 错误
     */
    private void ensureStreamAndGroup(String queueName) {
        try {
            // XADD 占位消息会自动创建 Stream，随后删除占位消息（Stream 本身保留）
            RecordId initId = stringRedisTemplate.opsForStream()
                    .add(StreamRecords.string(Map.of("init", "1")).withStreamKey(queueName));
            if (initId != null) {
                stringRedisTemplate.opsForStream().delete(queueName, initId.getValue());
            }
        } catch (Exception e) {
            log.warn("初始化 Stream 失败", e);
        }
        try {
            // 创建消费组（消费组已存在时会抛 BUSYGROUP 异常，忽略即可）
            stringRedisTemplate.opsForStream().createGroup(queueName, "g1");
        } catch (Exception e) {
            log.info("消费组已存在，跳过创建: {}", e.getMessage());
        }
    }

    /**
     * 应用关闭时立即中断消费者线程（ContextClosedEvent 在连接工厂停止之前发布，保证线程先退出）
     */
    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        consumerRunning = false;
        SECKILL_ORDER_EXECUTOR.shutdownNow();
    }

    /**
     * Bean 销毁时兜底关闭线程池，避免应用关闭后线程仍访问已销毁的 Redis 连接
     */
    @PreDestroy
    public void destroy() {
        consumerRunning = false;
        SECKILL_ORDER_EXECUTOR.shutdownNow();
    }

    private void handlerPendingList(String queueName) {
        while (consumerRunning) {
            try {
                //获取pending-list中的订单信息 xreadgroup group g1 c1 count 1 streams stream.orders 0
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );

                if (list == null || list.isEmpty()) {
                    //pending-list中没有未确认消息，退出循环
                    break;
                }
                //解析消息中的订单信息
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> values = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), true);
                handlerVoucherOrder(voucherOrder);
                //ack确认 sack stream.orders g1 id
                stringRedisTemplate.opsForStream().acknowledge(queueName, "g1", record.getId());
            } catch (Exception e) {
                //应用关闭时连接工厂已被销毁，不再处理消息，退出循环
                if (!consumerRunning) {
                    log.info("处理pending-list消息线程已停止，退出循环");
                    break;
                }
                //其他异常只记日志，不退出循环，保证消费者持续存活
                log.error("处理pending-list消息异常", e);
            }
        }
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

    /*
      秒杀优惠券主流程：
      1. 执行 Lua 脚本，原子化校验库存 + 一人一单 + 扣减库存
      2. 脚本返回 0 表示抢购成功，将订单放入阻塞队列后立即返回
      3. 异步消费者线程负责将队列中的订单写入数据库
     */
    /*@Override
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
    }*/

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
