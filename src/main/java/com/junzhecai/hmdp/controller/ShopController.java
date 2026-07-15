package com.junzhecai.hmdp.controller;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.dto.ShopDTO;
import com.junzhecai.hmdp.model.dto.ShopValidateGruop;
import com.junzhecai.hmdp.model.entity.Shop;
import com.junzhecai.hmdp.service.ShopService;
import com.junzhecai.hmdp.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/shop")
public class ShopController {

    @Autowired
    public ShopService shopService;

    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable Long id) throws InterruptedException {
        return shopService.queryShopById(id);
    }

    /**
     * 新增商铺信息
     *
     * @param shopDTO 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@Validated(ShopValidateGruop.SaveGroup.class) @RequestBody ShopDTO shopDTO) {
        // 写入数据库
        shopService.save(shopDTO);
        // 返回店铺id
        return Result.ok(shopDTO.getId());
    }

    /**
     * 更新商铺信息
     *
     * @param shopDTO 商铺数据
     * @return 无
     */
    @PutMapping
    public Result updateShop(@Validated(ShopValidateGruop.UpdateGroup.class) @RequestBody ShopDTO shopDTO) {
        return shopService.updateShopById(shopDTO);
    }

    /**
     * 根据商铺类型分页查询商铺信息
     *
     * @param typeId  商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信息
     *
     * @param name    商铺名称关键字
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Shop> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}
