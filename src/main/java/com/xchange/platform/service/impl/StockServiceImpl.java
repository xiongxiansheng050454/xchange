package com.xchange.platform.service.impl;

import com.xchange.platform.event.StockUpdatedEvent;
import com.xchange.platform.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockServiceImpl implements StockService {

    private final RedisScript<Long> stockDeductScript;
    private final ApplicationEventPublisher eventPublisher;

    // 使用@Qualifier注入专门用于Lua的StringRedisTemplate
    @Qualifier("stringRedisTemplateForLua")
    private final StringRedisTemplate stringRedisTemplate;

    private static final String STOCK_KEY_PREFIX = "stock:product:";

    @Override
    public boolean preloadStock(Long productId, Integer stock) {
        stringRedisTemplate.opsForValue().set("stock:product:" + productId, stock.toString());
        log.info("库存预热：productId={}, stock={}", productId, stock);
        return true;
    }

    @Override
    public Long deductStock(Long productId, Integer quantity) {
        String key = "stock:product:" + productId;

        try {
            Long result = stringRedisTemplate.execute(
                    stockDeductScript,
                    Collections.singletonList(key),
                    quantity.toString(),
                    productId.toString()
            );

            log.debug("扣库存结果：productId={}, quantity={}, result={}",
                    productId, quantity, result);
            eventPublisher.publishEvent(new StockUpdatedEvent(productId, Math.toIntExact(result)));
            return result;

        } catch (Exception e) {
            log.error("Lua执行异常：productId={}, error={}", productId, e.getMessage());
            return -99L; // 系统异常标记
        }
    }

    @Override
    public boolean rollbackStock(Long productId, Integer quantity) {
        String key = STOCK_KEY_PREFIX + productId;
        try {
            Long currentStock = stringRedisTemplate.opsForValue().increment(key, quantity);
            log.info("库存回滚成功: productId={}, quantity={}, currentStock={}",
                    productId, quantity, currentStock);

            return true;
        } catch (Exception e) {
            log.error("库存回滚失败: productId={}, error={}", productId, e.getMessage());
            return false;
        }
    }

    @Override
    public Integer getStock(Long productId) {
        String value = stringRedisTemplate.opsForValue().get("stock:product:" + productId);
        return value != null ? Integer.parseInt(value) : null;
    }
}
