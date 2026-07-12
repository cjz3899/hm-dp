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
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_user_info")
public class UserInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;
    private String city;
    private String introduce;//字数不超过128个字符
    private Integer fans;
    private Integer followee;
    private Boolean gender;//性别：0-男，1-女
    private LocalDateTime birthday;
    private Integer credits;
    private Boolean level;//会员等级，0-9，0代表未开通
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
