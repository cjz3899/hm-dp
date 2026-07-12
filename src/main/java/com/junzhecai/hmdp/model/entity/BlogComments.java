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
@TableName("tb_blog_comments")
public class BlogComments implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long blogId;

    /**
     * 关联的1级评论id，如果是一级评论，则值为0
     */
    private Long parentId;
    private Long answerId;
    private String content;
    private Integer liked;
    /**
     * 状态，0：正常，1：被举报，2：禁止查看
     */
    private Boolean status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
