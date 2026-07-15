package com.junzhecai.hmdp.service.Impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.ShopMapper;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.dto.ShopDTO;
import com.junzhecai.hmdp.model.entity.Shop;
import com.junzhecai.hmdp.model.vo.ShopVO;
import com.junzhecai.hmdp.service.Impl.support.ShopAssembler;
import com.junzhecai.hmdp.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.junzhecai.hmdp.utils.RedisConstants.*;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopAssembler shopAssembler;

    @Override
    public Result queryShopById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //从Redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //命中的是空对象，直接返回
        if ("NULL".equals(shopJson)) {
            return Result.fail("店铺不存在!");
        }
        //缓存命中（不为空且不是空字符串）
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            ShopVO shopVO = shopAssembler.toShopVO(shop);
            return Result.ok(shopVO);
        }
        //未命中，根据id查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "NULL", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("店铺不存在!");
        }
        //在数据库中查到，写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shopAssembler.toShopVO(shop));
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
