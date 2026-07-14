package com.junzhecai.hmdp.service.Impl.support;

import com.junzhecai.hmdp.model.entity.Shop;
import com.junzhecai.hmdp.model.vo.ShopVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class ShopAssembler {
    public abstract ShopVO toShopVO(Shop shop);
}
