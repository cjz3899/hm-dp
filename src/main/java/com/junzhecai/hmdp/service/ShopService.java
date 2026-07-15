package com.junzhecai.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.dto.ShopDTO;
import com.junzhecai.hmdp.model.entity.Shop;

public interface ShopService extends IService<Shop> {
    Result queryShopById(Long id) throws InterruptedException;

    Result updateShopById(ShopDTO shopDTO);

    void save(ShopDTO shopDTO);
}
