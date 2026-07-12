package com.junzhecai.hmdp.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("tb_blog")
public class Blog implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id",type = IdType.AUTO)
    private Long id;
    private Long shopId;
    private Long userId;
    @TableField(exist = false)//表示该字段不存在于数据库中
    private String icon;//用户头像
    @TableField(exist = false)
    private String name;
    @TableField(exist = false)
    private Boolean isLike;
    private String title;
    private String images;
    private String content;
    private Integer liked;
    private Integer comments;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
