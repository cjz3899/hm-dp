package com.junzhecai.hmdp.config;

import com.junzhecai.hmdp.interceptor.LoginInterceptor;
import com.junzhecai.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Autowired
    private LoginInterceptor loginInterceptor;
    @Autowired
    private RefreshTokenInterceptor refreshTokenInterceptor;

    //order：值越大，优先级越低
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 注册登录拦截器，拦截除白名单外的所有请求
        registry.addInterceptor(loginInterceptor)
                // 白名单路径：以下接口无需登录即可访问
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login"
                ).order(1);

        // 注册刷新令牌拦截器，拦截所有请求
        registry.addInterceptor(refreshTokenInterceptor).addPathPatterns("/**").order(0);
    }
}
