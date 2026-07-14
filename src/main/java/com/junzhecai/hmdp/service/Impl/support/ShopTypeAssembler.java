package com.junzhecai.hmdp.service.Impl.support;

import com.junzhecai.hmdp.model.entity.ShopType;
import com.junzhecai.hmdp.model.vo.ShopTypeVO;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public abstract class ShopTypeAssembler {
    public abstract ShopTypeVO toShopTypeVO(ShopType shopType);

    public abstract List<ShopTypeVO> toShopTypeVOList(List<ShopType> shopTypes);
}
