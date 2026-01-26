package com.xchange.platform.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单列表视图对象（轻量级）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "订单列表项")
public class OrderListVO {

    @Schema(description = "订单ID")
    private Long id;

    @Schema(description = "订单号")
    private String orderNo;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品封面图")
    private String productCoverImage;

    @Schema(description = "卖家ID")
    private Long sellerId;

    @Schema(description = "卖家昵称")
    private String sellerNickname;

    @Schema(description = "买家ID")
    private Long buyerId;

    @Schema(description = "买家昵称")
    private String buyerNickname;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "订单总价")
    private BigDecimal totalPrice;

    @Schema(description = "订单状态：PENDING_PAYMENT待付款, PAID已付款, PENDING待确认, SHIPPED已发货, COMPLETED已完成, CANCELLED已取消, REFUNDED已退款")
    private String status;

    @Schema(description = "创建时间")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    @Schema(description = "状态中文描述")
    public String getStatusDesc() {
        if (this.status == null) return "";
        return switch (this.status) {
            case "PENDING_PAYMENT" -> "待付款";
            case "PAID" -> "已付款";
            case "CONFIRMED" -> "已确认";
            case "SHIPPED" -> "已发货";
            case "COMPLETED" -> "已完成";
            case "CANCELLED" -> "已取消";
            default -> "未知";
        };
    }
}