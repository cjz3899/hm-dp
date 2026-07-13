package com.junzhecai.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.junzhecai.hmdp.mapper")
@SpringBootApplication
public class HmDpApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDpApplication.class, args);
    }

}
