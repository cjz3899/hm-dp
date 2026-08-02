package com.junzhecai.hmdp.service;

import com.junzhecai.hmdp.model.dto.Result;

public interface FollowService {
    Result follow(Long followUserId, Boolean isFollow);

    Result isFollow(Long followUserId);

}
