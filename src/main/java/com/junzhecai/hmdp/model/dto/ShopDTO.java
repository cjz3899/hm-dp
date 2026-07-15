package com.junzhecai.hmdp.model.dto;

import jakarta.validation.constraints.*;
import lombok.Data;


@Data
public class ShopDTO {
    @Null(groups = ShopValidateGruop.SaveGroup.class)
    @NotNull(groups = ShopValidateGruop.UpdateGroup.class)
    private Long id;
    @NotBlank(groups = ShopValidateGruop.SaveGroup.class, message = "店铺名称不能为空")
    private String name;
    @NotNull(message = "店铺类型不能为空")
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private Double x;
    private Double y;
    private Long avgPrice;
    @Min(value = 0, message = "评分不能小于0")
    @Max(value = 50, message = "评分不能大于50")
    private Integer score;
    private String openHours;
}
