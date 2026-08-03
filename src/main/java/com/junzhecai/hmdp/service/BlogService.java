package com.junzhecai.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.Blog;
import com.junzhecai.hmdp.model.entity.User;

import java.util.List;

public interface BlogService extends IService<Blog> {
    Result queryHotBlog(Integer current);

    Result queryBlogById(Long id);

    Result likeBlog(Long id);

    List<User> queryBlogLikes(Long id);

    List<User> followCommons(Long id);
}
