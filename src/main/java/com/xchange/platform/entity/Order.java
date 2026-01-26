package com.xchange.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tb_order")
public class Order {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    @TableField("item_id")  // 映射数据库字段
    private Long productId;

    private Long sellerId;
    private Long buyerId;
    private Integer quantity;         // 购买数量
    private BigDecimal price;
    private BigDecimal totalPrice;    // 订单总价
    private Integer status;           // 状态：0待付款 1已付款 2待确认 3已发货 4已完成 5已取消 6已退款
    private String trackingNumber;

    private String receiverName;      // 收货人姓名
    private String receiverPhone;     // 收货人手机
    private String receiverAddress;   // 收货地址
    private String buyerNote;         // 买家备注

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private LocalDateTime paymentDeadline; // 支付截止时间

    @TableLogic
    private Integer deleted;
}