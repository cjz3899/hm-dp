package com.junzhecai.hmdp.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.ShopMapper;
import com.junzhecai.hmdp.model.entity.Shop;
import com.junzhecai.hmdp.service.ShopService;
import org.springframework.stereotype.Service;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements ShopService {
}
