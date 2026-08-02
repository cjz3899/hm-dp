package com.junzhecai.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.junzhecai.hmdp.model.entity.Follow;
import org.springframework.data.repository.query.Param;


public interface FollowMapper extends BaseMapper<Follow> {
    int deleteByFollow(@Param("userId") Long userId, @Param("followUserId") Long followUserId);
}
