package com.junzhecai.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.junzhecai.hmdp.model.dto.LoginFormDTO;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.entity.User;
import jakarta.servlet.http.HttpSession;

public interface UserService extends IService<User> {
    Result sendCode(String phone, HttpSession session);

    Result loginOrRegister(LoginFormDTO loginForm, HttpSession session);
}
