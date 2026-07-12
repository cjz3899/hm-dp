package com.junzhecai.hmdp.model.dto;

import lombok.Data;

import java.util.List;

//滚动分页结果
@Data
public class ScrollResult {
    // 当前页返回的数据列表
    private List<?> list;
    // 当前页最小时间戳，作为下一次查询的游标
    private Long minTime;
    // 用于解决同一时间戳多条数据的偏移量
    private Integer offset;
}
