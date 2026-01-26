package com.xchange.platform.component;

import com.xchange.platform.event.StockUpdatedEvent;
import com.xchange.platform.utils.ElasticsearchUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;

/**
 * 库存同步监听器（异步）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockSyncListener {

    private final ElasticsearchUtil elasticsearchUtil;

    /**
     * 在事务提交后异步执行
     */
    @Async("asyncExecutor")
    @EventListener
    @Order(1)
    public void handleStockUpdate(StockUpdatedEvent event) {
        Long productId = event.getProductId();
        Integer newStock = event.getNewStock();

        try {
            log.info("【库存同步任务开始】productId={}, newStock={}", productId, newStock);

            // 延迟500ms，避免事务未完全提交
            Thread.sleep(500);

            elasticsearchUtil.updateProductStock(productId, newStock);

            log.info("【库存同步任务完成】productId={}", productId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("【库存同步中断】productId={}, error={}", productId, e.getMessage());
        } catch (Exception e) {
            log.error("【库存同步失败】productId={}, error={}", productId, e.getMessage());
        }
    }
}