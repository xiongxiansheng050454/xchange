package com.xchange.platform.component;

import com.xchange.platform.entity.Order;
import com.xchange.platform.entity.Product;
import com.xchange.platform.mapper.OrderMapper;
import com.xchange.platform.mapper.ProductMapper;
import com.xchange.platform.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean; // 新增
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Redis Key过期监听器：处理支付超时订单
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentTimeoutListener implements org.springframework.data.redis.connection.MessageListener, InitializingBean {

    private final RedisMessageListenerContainer redisMessageListenerContainer; // 自动注入
    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final OrderService orderService;

    /**
     * 初始化时注册监听器
     */
    @Override
    public void afterPropertiesSet() {
        // 监听Redis过期事件（__keyevent@*__:expired）
        redisMessageListenerContainer.addMessageListener(
                this,
                new PatternTopic("__keyevent@*__:expired")
        );
        log.info("【支付超时监听器】已注册到Redis");
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.debug("【Redis过期事件】key={}", expiredKey);

        // 只处理支付超时Key
        if (expiredKey.startsWith("order:payment:timeout:")) {
            Long orderId = Long.parseLong(expiredKey.split(":")[3]);
            handlePaymentTimeout(orderId);
        }
    }

    /**
     * 处理支付超时：自动取消订单并回滚库存
     */
    private void handlePaymentTimeout(Long orderId) {
        log.info("【支付超时处理】开始，orderId={}", orderId);

        try {
            // 1. 查询订单
            Order order = orderMapper.selectById(orderId);
            if (order == null || order.getDeleted() == 1) {
                log.warn("订单不存在或已删除: orderId={}", orderId);
                return;
            }

            // 2. 检查状态（只有待付款状态才处理）
            if (order.getStatus() != 0) { // 0=待付款
                log.info("订单状态不是待付款，跳过处理: orderId={}, status={}", orderId, order.getStatus());
                return;
            }

            // 3. 触发取消事件
            orderService.cancelOrder(order.getBuyerId(), orderId);

            // 4. 回滚库存
            rollbackStock(order.getProductId(), order.getQuantity());
            log.info("【支付超时】订单已自动取消，库存已回滚: orderId={}", orderId);

        } catch (Exception e) {
            log.error("【支付超时处理异常】orderId={}, error={}", orderId, e.getMessage(), e);
        }
    }

    /**
     * 回滚库存
     */
    private void rollbackStock(Long productId, Integer quantity) {
        try {
            com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Product> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<>();
            wrapper.eq(Product::getId, productId)
                    .setSql("stock = stock + {0}", quantity); // 原子增加

            int updateCount = productMapper.update(null, wrapper);

            if (updateCount > 0) {
                log.info("【库存回滚成功】productId={}, quantity={}", productId, quantity);
            } else {
                log.error("【库存回滚失败】productId={}, quantity={}", productId, quantity);
            }
        } catch (Exception e) {
            log.error("【库存回滚异常】productId={}, error={}", productId, e.getMessage());
        }
    }
}