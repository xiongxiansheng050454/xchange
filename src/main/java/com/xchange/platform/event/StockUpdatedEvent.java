package com.xchange.platform.event;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 库存更新事件
 */
@Data
@AllArgsConstructor
public class StockUpdatedEvent {
    private Long productId;
    private Integer newStock;
}