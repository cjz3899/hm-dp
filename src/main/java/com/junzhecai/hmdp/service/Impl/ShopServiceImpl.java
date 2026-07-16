package com.junzhecai.hmdp.service.Impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.ShopMapper;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.dto.ShopDTO;
import com.junzhecai.hmdp.model.entity.RedisData;
import com.junzhecai.hmdp.model.entity.Shop;
import com.junzhecai.hmdp.model.vo.ShopVO;
import com.junzhecai.hmdp.service.Impl.support.ShopAssembler;
import com.junzhecai.hmdp.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.junzhecai.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopAssembler shopAssembler;

    @Override
    public Result queryShopById(Long id) throws InterruptedException {
        //ShopVO shopVO = queryWithPassThrough(id);

        //shopVO shopVO = queryWithMutex(id);

        ShopVO shopVO = queryWithLogicalExpire(id);
        if (shopVO == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shopVO);
    }

    //防止缓存击穿（互斥锁）
    private ShopVO queryWithMutex(Long id) throws InterruptedException {
        String key = CACHE_SHOP_KEY + id;
        while (true) {
            //从Redis中查询缓存
            String shopJson = stringRedisTemplate.opsForValue().get(key);
            //命中的是空对象，直接返回
            if ("NULL".equals(shopJson)) {
                return null;
            }
            //缓存命中（不为空且不是空字符串）
            if (StrUtil.isNotBlank(shopJson)) {
                Shop shop = JSONUtil.toBean(shopJson, Shop.class);
                return shopAssembler.toShopVO(shop);
            }
            String lockKey = LOCK_SHOP_KEY + id;
            //尝试获取锁
            boolean isLocked = tryLock(lockKey);
            if (!isLocked) {
                //获取锁失败，让线程等待
                Thread.sleep(50);
                continue;
            }
            //获取锁成功
            try {
                /*双检缓存：
                 * 两次检查：锁外查1次（过滤绝大多数流量），锁内查1次（防止重复查库），缺一不可，少了锁外会堵死，少了锁内会穿透 */
                shopJson = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(shopJson)) {
                    Shop shop = JSONUtil.toBean(shopJson, Shop.class);
                    return shopAssembler.toShopVO(shop);
                }
                if ("NULL".equals(shopJson)) {
                    return null;
                }
                //未命中，根据id查询数据库
                Shop shop = getById(id);
                //模拟查询数据库耗时
                Thread.sleep(200);
                if (shop == null) {
                    stringRedisTemplate.opsForValue().set(key, "NULL", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                //在数据库中查到，写入Redis
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
                return shopAssembler.toShopVO(shop);
            } finally {
                unlock(lockKey);
            }
        }
    }

    //防止缓存穿透
    private ShopVO queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //命中的是空对象，直接返回
        if ("NULL".equals(shopJson)) {
            return null;
        }
        //缓存命中（不为空且不是空字符串）
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shopAssembler.toShopVO(shop);
        }
        //未命中，根据id查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "NULL", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //在数据库中查到，写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shopAssembler.toShopVO(shop);
    }


    //引入缓存池
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    //防止缓存击穿（逻辑过期）
    private ShopVO queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //未命中缓存
        if (StrUtil.isBlank(shopJson)) {
            return null;
        }
        //命中缓存
        //将json反序列化为RedisData对象，再把RedisData对象中的data字段反序列化为ShopVO对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        ShopVO shopVO = JSONUtil.toBean((JSONObject) redisData.getData(), ShopVO.class);
        //判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期，返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return shopVO;
        }
        //已过期，需要缓存重建
        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(key);
        //获取锁成功
        if (isLock) {
            //开启一个新线程，实现缓存重建（使用缓存池）
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //获取锁失败，返回过期店铺信息
        return shopVO;
    }

    //尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //删除锁
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


    //将Shop对象保存到Redis中，并设置过期时间
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);//模拟查询数据库耗时
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    @Override
    public Result updateShopById(ShopDTO shopDTO) {
        Shop shop = shopAssembler.toShop(shopDTO);
        //更新数据库
        updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());
        return Result.ok();
    }

    @Override
    public void save(ShopDTO shopDTO) {
        return;
    }

}
