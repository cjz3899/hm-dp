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
@TableName("tb_voucher_order")
public class VoucherOrder implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.INPUT)
    private Long id;
    private Long userId;
    private Long voucherId;
    private Integer payType;//支付方式 1：余额支付；2：支付宝；3：微信
    private Integer status;//订单状态，1：未支付；2：已支付；3：已核销；4：已取消；5：退款中；6：已退款
    private LocalDateTime createTime;
    private LocalDateTime payTime;
    private LocalDateTime useTime;//核销时间
    private LocalDateTime refundTime;//退款时间
    private LocalDateTime updateTime;
}
