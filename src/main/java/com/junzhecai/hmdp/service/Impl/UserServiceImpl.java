package com.junzhecai.hmdp.service.Impl;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.junzhecai.hmdp.mapper.UserMapper;
import com.junzhecai.hmdp.model.dto.LoginFormDTO;
import com.junzhecai.hmdp.model.dto.Result;
import com.junzhecai.hmdp.model.dto.UserDTO;
import com.junzhecai.hmdp.model.entity.User;
import com.junzhecai.hmdp.serializer.UserDTOMapSerializer;
import com.junzhecai.hmdp.service.Impl.support.UserAssembler;
import com.junzhecai.hmdp.service.UserService;
import com.junzhecai.hmdp.utils.RegexUtils;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.junzhecai.hmdp.utils.RedisConstants.*;
import static com.junzhecai.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private UserDTOMapSerializer userDTOMapSerializer;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误!");
        }
        String code = RandomUtil.randomNumbers(6);
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("发送短信验证码成功，验证码：{}", code);//模拟验证码发送过程，实际开发中应通过短信服务发送验证码
        return Result.ok();
    }

    @Override
    public Result loginOrRegister(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误!");
        }
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        String code = loginForm.getCode();
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误!");
        }

        User user = query().eq("phone", phone).one();
        if (user == null) {
            //数据库里没有这个用户，则创建一个新的
            user = createUserWithPhone(phone);
        }
        String token = UUID.randomUUID(true).toString();
        UserDTO userDTO = UserAssembler.toUserDTO(user);//将用户信息转换为用户DTO
        /*Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));*/
        Map<String, String> userMap = userDTOMapSerializer.serialize(userDTO);
        String key = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key, userMap);
        stringRedisTemplate.expire(key, LOGIN_USER_TTL, TimeUnit.MINUTES);
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(6));
        save(user);
        return user;
    }
}
