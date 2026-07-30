package com.junzhecai.hmdp.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.junzhecai.hmdp.controller.Support.UserVoAssembler;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.dto.UserDTO;
import com.junzhecai.hmdp.model.entity.Blog;
import com.junzhecai.hmdp.model.entity.User;
import com.junzhecai.hmdp.service.BlogService;
import com.junzhecai.hmdp.utils.SystemConstants;
import com.junzhecai.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/blog")
public class BlogController {

    @Autowired
    private BlogService blogService;
    @Autowired
    private UserVoAssembler userVoAssembler;

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        blogService.save(blog);
        // 返回id
        return Result.ok(blog.getId());
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable("id") Long id) {
        return blogService.queryBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        List<User> users = blogService.queryBlogLikes(id);
        return Result.ok(userVoAssembler.toUserVOList(users));
    }
}
