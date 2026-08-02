package com.junzhecai.hmdp.service.Impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.FollowMapper;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.Follow;
import com.junzhecai.hmdp.service.FollowService;
import com.junzhecai.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements FollowService {
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            //关注，添加数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            save(follow);
        } else {
            //取消关注，删除数据
            /*remove(new QueryWrapper<Follow>()
                    .eq("user_id", userId).eq("follow_user_id", followUserId));*/
            baseMapper.deleteByFollow(userId, followUserId);
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Long count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }
}
