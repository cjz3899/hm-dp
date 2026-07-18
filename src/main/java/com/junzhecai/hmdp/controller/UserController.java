package com.junzhecai.hmdp.controller;

import com.junzhecai.hmdp.controller.Support.UserVoAssembler;
import com.junzhecai.hmdp.model.dto.LoginFormDTO;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.dto.UserDTO;
import com.junzhecai.hmdp.model.entity.UserInfo;
import com.junzhecai.hmdp.service.UserInfoService;
import com.junzhecai.hmdp.service.UserService;
import com.junzhecai.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @Autowired
    private UserInfoService userInfoService;

    @Autowired
    private UserVoAssembler userVoAssembler;

    // TODO 自定义注解实现校验器功能

    /**
     * 发送手机验证码
     */
    @PostMapping("/code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        // 发送短信验证码并保存验证码
        return userService.sendCode(phone, session);
    }

    /**
     * 登录注册功能，如果手机号已存在，则登录；如果手机号不存在，则创建新用户
     *
     * @param loginForm 登录参数，包含手机号、验证码；或者手机号、密码
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        // 实现登录功能
        return userService.loginOrRegister(loginForm, session);
    }

    /**
     * 登出功能
     *
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout() {
        // TODO 实现登出功能
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me() {
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(userVoAssembler.toUserVO(user));
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId) {
        // 查询详情
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 返回
        return Result.ok(info);
    }
}

