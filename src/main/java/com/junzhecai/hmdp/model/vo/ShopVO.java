package com.junzhecai.hmdp.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShopVO {
    private Long id;
    private String name;
    private Long typeId;
    private String images;
    private String area;
    private String address;
    private Double x;
    private Double y;
    private Long avgPrice;
    private Integer sold;
    private Integer comments;
    private Integer score;//评分，1-5，乘10保存
    private String openHours;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Double distance;
}
