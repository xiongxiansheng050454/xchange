package com.xchange.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单分页查询DTO（支持买家/卖家双视角）
 */
@Data
@Schema(description = "订单查询条件")
public class OrderQueryDTO {

    @Schema(description = "页码（从1开始）", example = "1")
    @Min(value = 1, message = "页码必须大于0")
    private Integer pageNum = 1;

    @Schema(description = "每页数量", example = "10")
    @Min(value = 1, message = "每页数量必须大于0")
    private Integer pageSize = 10;

    @Schema(description = "订单状态筛选（PENDING_PAYMENT, PAID, CONFIRMED, SHIPPED, COMPLETED, CANCELLED），不传则查询全部")
    private String status;

    @Schema(description = "订单号模糊搜索")
    private String orderNo;

    @Schema(description = "商品名称模糊搜索")
    private String productName;

    @Schema(description = "开始时间（格式：yyyy-MM-dd HH:mm:ss）")
    private LocalDateTime startTime;

    @Schema(description = "结束时间（格式：yyyy-MM-dd HH:mm:ss）")
    private LocalDateTime endTime;

    @Schema(description = "排序字段（create_time, price），默认create_time", example = "create_time")
    private String sortBy = "create_time";

    @Schema(description = "是否升序，默认false（降序）", example = "false")
    private Boolean asc = false;
}