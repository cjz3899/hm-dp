package com.junzhecai.hmdp.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
//Lombok 自动生成 equals() 和 hashCode() 方法，callSuper = false 表示不调用父类的 equals/hashCode（即只比较当前类的字
@EqualsAndHashCode(callSuper = false)

//Lombok 使 setter 方法返回 this（当前对象），支持链式调用，如 user.setName("xxx").setAge(18)
@Accessors(chain = true)

//MyBatis-Plus 注解，指定该实体类对应的数据库表名为 tb_user，用于自动映射SQL操作
@TableName("tb_user")
public class User implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    //MyBatis-Plus 注解，指定 id 字段对应的数据库表字段为 id，并设置主键生成策略为自增
    @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    private String phone;
    private String password;
    private String nickName;
    private String icon="";//用户头像
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
