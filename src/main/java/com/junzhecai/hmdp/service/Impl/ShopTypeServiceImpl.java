package com.junzhecai.hmdp.service.Impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.ShopTypeMapper;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.ShopType;
import com.junzhecai.hmdp.model.vo.ShopTypeVO;
import com.junzhecai.hmdp.service.Impl.support.ShopTypeAssembler;
import com.junzhecai.hmdp.service.ShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.junzhecai.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.junzhecai.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements ShopTypeService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ShopTypeAssembler shopTypeAssembler;

    @Override
    public Result queryTypeList() {
        String key = CACHE_SHOP_TYPE_KEY + "list";
        String shopTypeJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopTypeJson)) {
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            List<ShopTypeVO> shopTypeVOs = shopTypeAssembler.toShopTypeVOList(shopTypes);
            return Result.ok(shopTypeVOs);
        }
        List<ShopType> typeList = lambdaQuery().orderByAsc(ShopType::getSort).list();
        // TODO 看看要不要做缓存穿透处理
        if (typeList == null || typeList.isEmpty()) {
            return Result.fail("店铺类型不存在!");
        }
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(typeList), CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        List<ShopTypeVO> shopTypeVOs = shopTypeAssembler.toShopTypeVOList(typeList);
        return Result.ok(shopTypeVOs);
    }
}
