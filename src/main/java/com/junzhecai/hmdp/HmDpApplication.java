package com.junzhecai.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy(exposeProxy = true)//启用AspectJ的自动代理
@MapperScan("com.junzhecai.hmdp.mapper")
@SpringBootApplication
public class HmDpApplication {
    public static void main(String[] args) {
        SpringApplication.run(HmDpApplication.class, args);
    }

}
