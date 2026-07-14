package com.junzhecai.hmdp.service.Impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.ShopMapper;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.Shop;
import com.junzhecai.hmdp.model.vo.ShopVO;
import com.junzhecai.hmdp.service.Impl.support.ShopAssembler;
import com.junzhecai.hmdp.service.ShopService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.junzhecai.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.junzhecai.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;

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
        //缓存命中
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            ShopVO shopVO = shopAssembler.toShopVO(shop);
            return Result.ok(shopVO);
        }
        //缓存未命中，根据id查询数据库
        Shop shop = getById(id);
        if (shop == null) {
            return Result.fail("店铺不存在!");
        }
        //在数据库中查到，写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return Result.ok(shopAssembler.toShopVO(shop));
    }

}
