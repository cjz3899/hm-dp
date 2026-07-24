package com.junzhecai.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.junzhecai.hmdp.model.entity.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.junzhecai.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

@Slf4j
@Component
public class CacheClient {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    //缓存数据到Redis
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    //缓存数据到Redis时设置逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //工具类要兼容所有的实体类类型，因此使用泛型
    /*public <R, ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        //从Redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //命中的是空对象，直接返回
        if ("NULL".equals(json)) {
            return null;
        }
        //缓存命中（不为空且不是空字符串）
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        //未命中，根据id查询数据库
        R r = dbFallback.apply(id);
        if (r == null) {
            stringRedisTemplate.opsForValue().set(key, "NULL", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //在数据库中查到，写入Redis
        this.set(key, r, time, unit);
        return r;
    }*/


    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) throws InterruptedException {
        String key = keyPrefix + id;
        while (true) {
            //从Redis中查询缓存
            String json = stringRedisTemplate.opsForValue().get(key);
            //命中的是空对象，直接返回
            if ("NULL".equals(json)) {
                return null;
            }
            //缓存命中（不为空且不是空字符串）
            if (StrUtil.isNotBlank(json)) {
                return JSONUtil.toBean(json, type);
            }
            String lockKey = RedisConstants.toLockKey(keyPrefix) + id;
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
                json = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(json)) {
                    return JSONUtil.toBean(json, type);
                }
                if ("NULL".equals(json)) {
                    return null;
                }
                //未命中，根据id查询数据库
                R r = dbFallback.apply(id);
                //模拟查询数据库耗时
                Thread.sleep(200);
                if (r == null) {
                    stringRedisTemplate.opsForValue().set(key, "NULL", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                //在数据库中查到，写入Redis
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time, unit);
                return r;
            } finally {
                unlock(lockKey);
            }
        }
    }


    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public <R, ID> R queryWithLogicalExpire(String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //未命中缓存
        if (StrUtil.isBlank(json)) {
            return null;
        }
        //命中缓存
        //将json反序列化为RedisData对象，再把RedisData对象中的data字段反序列化为R对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        //判断是否过期
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期，返回店铺信息
        if (expireTime.isAfter(LocalDateTime.now())) {
            return r;
        }
        //已过期，需要缓存重建
        //获取互斥锁
        String lockKey = RedisConstants.toLockKey(keyPrefix) + id;
        boolean isLock = tryLock(key);
        //获取锁成功
        if (isLock) {
            //开启一个新线程，实现缓存重建（使用缓存池）
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    R newR = dbFallback.apply(id);
                    this.setWithLogicalExpire(key, newR, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //获取锁失败，返回过期店铺信息
        return r;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


}
