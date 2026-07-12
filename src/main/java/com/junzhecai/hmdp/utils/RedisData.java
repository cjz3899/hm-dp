package com.junzhecai.hmdp.utils;

import lombok.Data;

import java.time.LocalDateTime;

//缓存封装 DTO，用于逻辑过期
@Data
public class RedisData {
    private LocalDateTime expireTime;//逻辑过期时间，存入 Redis
    private Object data;//实际要缓存的数据
}