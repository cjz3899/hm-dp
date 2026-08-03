package com.junzhecai.hmdp.service.Impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.BlogMapper;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.dto.UserDTO;
import com.junzhecai.hmdp.model.entity.Blog;
import com.junzhecai.hmdp.model.entity.User;
import com.junzhecai.hmdp.service.BlogService;
import com.junzhecai.hmdp.service.UserService;
import com.junzhecai.hmdp.utils.SystemConstants;
import com.junzhecai.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.junzhecai.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements BlogService {
    @Autowired
    private UserService userService;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户+点赞状态
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        //查询blog
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        //查询发布该blog的用户id
        queryBlogUser(blog);
        //查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //获取当前用户
        UserDTO userDTO = UserHolder.getUser();
        if (userDTO == null) {
            //未登录，无需查询是否点赞
            return;
        }
        Long userId = userDTO.getId();
        String key = BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            //若未点赞，可以点赞
            //数据库点赞数+1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            //若已点赞，取消点赞
            //数据库点赞数-1
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public List<User> queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        //查询top5的用户id
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Collections.emptyList();
        }
        //解析用户id
        List<Long> ids = top5.stream().map(Long::valueOf).toList();
        String idsStr = StrUtil.join(",", ids);
        //查询用户 where id in (?,?) oreder by field(id,?,?)
        //根据给定顺序显示
        return userService.query()
                .in("id", ids).last("order by field(id," + idsStr + ")").list()
                .stream()
                .toList();
    }

    @Override
    public List<User> followCommons(Long id) {
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //当前用户关注的用户
        String key2 = "follows:" + id;
        //获取交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Collections.emptyList();
        }
        //解析用户id
        List<Long> ids = intersect.stream().map(Long::valueOf).toList();
        //查询用户
        return userService.listByIds(ids);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }


}
