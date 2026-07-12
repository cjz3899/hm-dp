package com.junzhecai.hmdp.controller;

import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.ShopType;
import com.junzhecai.hmdp.service.ShopTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
    @Autowired
    private ShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<ShopType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}
