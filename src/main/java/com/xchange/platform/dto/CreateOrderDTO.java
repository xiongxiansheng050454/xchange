package com.xchange.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建订单请求DTO
 */
@Data
@Builder
@NoArgsConstructor          // 添加：无参构造函数（Jackson必需）
@AllArgsConstructor         // 添加：全参构造函数（兼容Builder）
@Schema(description = "创建订单请求")
public class CreateOrderDTO {

    @Schema(description = "商品ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "商品ID不能为空")
    private Long productId;

    @Schema(description = "购买数量", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "购买数量不能为空")
    @Min(value = 1, message = "购买数量至少为1")
    private Integer quantity;

    @Schema(description = "收货人姓名")
    private String receiverName;

    @Schema(description = "收货人手机")
    private String receiverPhone;

    @Schema(description = "收货地址")
    private String receiverAddress;

    @Schema(description = "买家备注")
    private String buyerNote;
}