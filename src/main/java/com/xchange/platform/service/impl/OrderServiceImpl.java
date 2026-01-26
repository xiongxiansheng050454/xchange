package com.xchange.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xchange.platform.dto.CreateOrderDTO;
import com.xchange.platform.dto.OrderQueryDTO;
import com.xchange.platform.entity.Order;
import com.xchange.platform.entity.Product;
import com.xchange.platform.entity.User;
import com.xchange.platform.event.StockUpdatedEvent;
import com.xchange.platform.mapper.OrderMapper;
import com.xchange.platform.mapper.ProductMapper;
import com.xchange.platform.mapper.UserMapper;
import com.xchange.platform.orderstate.OrderEvents;
import com.xchange.platform.orderstate.OrderStates;
import com.xchange.platform.service.OrderService;
import com.xchange.platform.utils.RedisUtil;
import com.xchange.platform.vo.OrderListVO;
import com.xchange.platform.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 订单服务实现（枚举+乐观锁）
 * 移除状态机，显式控制状态流转
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final RedisUtil redisUtil;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 状态流转规则定义 ====================
    // 定义：当前状态 -> 允许的事件
    private static final Map<OrderStates, Set<OrderEvents>> VALID_TRANSITIONS = new EnumMap<>(OrderStates.class);

    static {
        VALID_TRANSITIONS.put(OrderStates.PENDING_PAYMENT, Set.of(OrderEvents.PAY, OrderEvents.CANCEL));
        VALID_TRANSITIONS.put(OrderStates.PAID, Set.of(OrderEvents.CONFIRM, OrderEvents.CANCEL));
        VALID_TRANSITIONS.put(OrderStates.CONFIRMED, Set.of(OrderEvents.SHIP, OrderEvents.CANCEL));
        VALID_TRANSITIONS.put(OrderStates.SHIPPED, Set.of(OrderEvents.RECEIVE, OrderEvents.CANCEL));
        VALID_TRANSITIONS.put(OrderStates.COMPLETED, Set.of()); // 终态
        VALID_TRANSITIONS.put(OrderStates.CANCELLED, Set.of()); // 终态
    }

    // ==================== 核心接口：下单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long buyerId, CreateOrderDTO createOrderDTO) {
        log.info("【乐观锁-下单开始】buyerId={}, productId={}, quantity={}",
                buyerId, createOrderDTO.getProductId(), createOrderDTO.getQuantity());

        long startTime = System.currentTimeMillis();

        try {
            // 1. 参数校验
            validateCreateOrderDTO(createOrderDTO);

            // 2. 查询商品
            Product product = productMapper.selectById(createOrderDTO.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在或已下架");
            }
            validateProduct(product, createOrderDTO.getQuantity());

            // 3. 乐观锁扣减库存（带重试）
            deductStockWithOptimisticLock(product.getId(), createOrderDTO.getQuantity());

            // 4. 生成订单（初始状态：待付款，version=0）
            Order order = buildOrder(buyerId, product, createOrderDTO);
            orderMapper.insert(order);

            // 5. 设置支付超时任务（30分钟）
            schedulePaymentTimeout(order.getId());

            log.info("【下单成功】orderId={}, orderNo={}, status={}, costTime={}ms",
                    order.getId(), order.getOrderNo(),
                    OrderStates.PENDING_PAYMENT.name(),
                    System.currentTimeMillis() - startTime);

            return convertToVO(order, product, buyerId);

        } catch (RuntimeException e) {
            log.error("【下单失败】buyerId={}, error={}", buyerId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("【下单异常】buyerId={}, error={}", buyerId, e.getMessage(), e);
            throw new RuntimeException("系统繁忙，请稍后重试");
        }
    }

    // ==================== 支付流程 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentSuccess(Long orderId, String paymentId) {
        log.info("【支付回调】orderId={}, paymentId={}", orderId, paymentId);

        try {
            // 1. 查询订单（获取当前状态和版本号）
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new RuntimeException("订单不存在");
            }

            // 订单已取消 → 触发自动退款
            if (order.getStatus() == OrderStates.CANCELLED.ordinal()) {
                log.error("【支付-订单冲突】orderId={}, 发起自动退款", orderId);

                // 调用支付宝/微信退款接口

                return; // 正常返回，避免支付宝重试
            }

            // 2. 幂等检查：只有待付款状态才处理
            OrderStates currentState = OrderStates.values()[order.getStatus()];
            if (currentState != OrderStates.PENDING_PAYMENT) {
                log.warn("【支付幂等】订单状态不是待付款，跳过处理: orderId={}, status={}",
                        orderId, currentState);
                return;
            }

            // 3. 状态前置校验
            validateStatusTransition(currentState, OrderEvents.PAY);

            // 4. 乐观锁更新状态
            boolean updated = updateOrderStatus(orderId, currentState, OrderStates.PAID, order.getVersion());
            if (!updated) {
                log.warn("【乐观锁冲突】支付更新失败: orderId={}, version={}", orderId, order.getVersion());
                throw new RuntimeException("支付处理失败，请重试");
            }

            // 5. 清理支付超时Key
            redisUtil.delete("order:payment:timeout:" + orderId);

            log.info("【支付成功】orderId={}, paymentId={}, newStatus={}",
                    orderId, paymentId, OrderStates.PAID);

        } catch (RuntimeException e) {
            log.error("【支付失败】orderId={}, error={}", orderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("【支付异常】orderId={}, error={}", orderId, e.getMessage(), e);
            throw new RuntimeException("支付回调处理失败");
        }
    }

    // ==================== 履约流程 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmOrder(Long sellerId, Long orderId) {
        log.info("【卖家确认订单】sellerId={}, orderId={}", sellerId, orderId);

        // 1. 校验订单归属权
        Order order = validateOrderOwnership(sellerId, orderId);

        // 2. 获取当前状态
        OrderStates currentState = OrderStates.values()[order.getStatus()];
        if (currentState != OrderStates.PAID) {
            throw new RuntimeException("只有已付款订单才能确认，当前状态: " + currentState);
        }

        // 3. 状态校验
        validateStatusTransition(currentState, OrderEvents.CONFIRM);

        // 4. 乐观锁更新
        boolean updated = updateOrderStatus(orderId, currentState, OrderStates.CONFIRMED, order.getVersion());
        if (!updated) {
            throw new RuntimeException("订单确认失败，请重试");
        }

        log.info("【订单确认成功】orderId={}, sellerId={}", orderId, sellerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void shipOrder(Long sellerId, Long orderId, String trackingNumber) {
        log.info("【卖家发货】sellerId={}, orderId={}, tracking={}", sellerId, orderId, trackingNumber);

        // 1. 校验
        Order order = validateOrderOwnership(sellerId, orderId);
        OrderStates currentState = OrderStates.values()[order.getStatus()];
        if (currentState != OrderStates.CONFIRMED) {
            throw new RuntimeException("只有已确认订单才能发货，当前状态: " + currentState);
        }

        // 2. 状态校验
        validateStatusTransition(currentState, OrderEvents.SHIP);

        // 3. 更新物流信息（如果提供）
        if (trackingNumber != null && !trackingNumber.isBlank()) {
            LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Order::getId, orderId)
                    .set(Order::getTrackingNumber, trackingNumber);
            orderMapper.update(null, wrapper);
        }

        // 4. 乐观锁更新状态
        boolean updated = updateOrderStatus(orderId, currentState, OrderStates.SHIPPED, order.getVersion());
        if (!updated) {
            throw new RuntimeException("发货失败，请重试");
        }

        log.info("【发货成功】orderId={}, sellerId={}, tracking={}", orderId, sellerId, trackingNumber);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void receiveOrder(Long buyerId, Long orderId) {
        log.info("【买家确认收货】buyerId={}, orderId={}", buyerId, orderId);

        // 1. 校验买家身份
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            throw new RuntimeException("无权操作该订单");
        }

        // 2. 检查状态
        OrderStates currentState = OrderStates.values()[order.getStatus()];
        if (currentState != OrderStates.SHIPPED) {
            throw new RuntimeException("只有已发货订单才能确认收货，当前状态: " + currentState);
        }

        // 3. 状态校验
        validateStatusTransition(currentState, OrderEvents.RECEIVE);

        // 4. 乐观锁更新
        boolean updated = updateOrderStatus(orderId, currentState, OrderStates.COMPLETED, order.getVersion());
        if (!updated) {
            throw new RuntimeException("确认收货失败，请重试");
        }

        log.info("【交易完成】orderId={}, buyerId={}", orderId, buyerId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long userId, Long orderId) {
        log.info("【取消订单】userId={}, orderId={}", userId, orderId);

        // 1. 校验身份（卖家或买家）
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getSellerId().equals(userId) && !order.getBuyerId().equals(userId)) {
            throw new RuntimeException("无权取消该订单");
        }

        // 2. 检查状态（已完成/已取消不能再次取消）
        OrderStates currentState = OrderStates.values()[order.getStatus()];
        if (currentState == OrderStates.COMPLETED || currentState == OrderStates.CANCELLED) {
            throw new RuntimeException("已完成或已取消的订单无法再次取消");
        }

        // 3. 状态校验
        validateStatusTransition(currentState, OrderEvents.CANCEL);

        // 4. 乐观锁更新
        boolean updated = updateOrderStatus(orderId, currentState, OrderStates.CANCELLED, order.getVersion());
        if (!updated) {
            throw new RuntimeException("取消订单失败，请重试");
        }

        // 5. 回滚库存
        rollbackStock(order.getProductId(), order.getQuantity());

        // 6. 清理支付超时Key（如果存在）
        redisUtil.delete("order:payment:timeout:" + orderId);

        log.info("【订单已取消】orderId={}, userId={}, oldStatus={}", orderId, userId, currentState);
    }

    @Override
    public OrderStates getCurrentState(Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        return OrderStates.values()[order.getStatus()];
    }

    // ==================== 通用方法抽取 ====================

    /**
     * 状态前置校验：检查当前状态是否允许执行该事件
     * @param currentState 当前状态
     * @param event 触发事件
     */
    private void validateStatusTransition(OrderStates currentState, OrderEvents event) {
        Set<OrderEvents> allowedEvents = VALID_TRANSITIONS.get(currentState);
        if (allowedEvents == null || !allowedEvents.contains(event)) {
            throw new RuntimeException(String.format(
                    "非法状态流转: 当前状态[%s]不允许执行[%s]", currentState, event));
        }
    }

    /**
     * 强制乐观锁更新订单状态
     * @param orderId 订单ID
     * @param currentState 当前状态（预期值）
     * @param nextState 目标状态
     * @param currentVersion 当前版本号
     * @return 是否更新成功
     */
    private boolean updateOrderStatus(Long orderId, OrderStates currentState,
                                      OrderStates nextState, Integer currentVersion) {
        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId)
                .eq(Order::getStatus, currentState.ordinal()) // 状态一致性校验
                .eq(Order::getVersion, currentVersion)        // 乐观锁
                .set(Order::getStatus, nextState.ordinal())   // 更新状态
                .set(Order::getVersion, currentVersion + 1);  // 版本号+1

        int updated = orderMapper.update(null, wrapper);

        if (updated > 0) {
            log.info("【状态更新成功】orderId={}, {} -> {}, version={}+{}",
                    orderId, currentState, nextState, currentVersion, currentVersion + 1);
        } else {
            log.warn("【乐观锁冲突】orderId={}, currentVersion={}", orderId, currentVersion);
        }

        return updated > 0;
    }

    // ==================== 辅助方法 ====================

    private void validateCreateOrderDTO(CreateOrderDTO dto) {
        if (dto.getQuantity() == null || dto.getQuantity() <= 0) {
            throw new RuntimeException("购买数量必须大于0");
        }
        if (dto.getProductId() == null) {
            throw new RuntimeException("商品ID不能为空");
        }
    }

    private void validateProduct(Product product, Integer quantity) {
        if (product.getStock() < quantity) {
            throw new RuntimeException("库存不足，仅剩" + product.getStock() + "件");
        }
        if (product.getPrice() == null || product.getPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("商品价格异常");
        }
    }

    /**
     * 乐观锁扣减库存（重试3次）
     */
    private void deductStockWithOptimisticLock(Long productId, Integer quantity) {
        for (int retry = 0; retry < 3; retry++) {
            Product product = productMapper.selectById(productId);

            if (product.getStock() < quantity) {
                throw new RuntimeException("库存不足，仅剩" + product.getStock() + "件");
            }

            LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Product::getId, productId)
                    .eq(Product::getVersion, product.getVersion())
                    .ge(Product::getStock, quantity)
                    .setSql("stock = stock - {0}", quantity)
                    .set(Product::getVersion, product.getVersion() + 1);

            int updateCount = productMapper.update(null, wrapper);

            if (updateCount > 0) {
                Integer newStock = product.getStock() - quantity;
                eventPublisher.publishEvent(new StockUpdatedEvent(productId, newStock));
                log.info("【乐观锁扣减成功】productId={}, quantity={}, version={}",
                        productId, quantity, product.getVersion());
                return;
            }

            // 重试等待
            try {
                Thread.sleep((long) (Math.pow(2, retry) * 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        throw new RuntimeException("库存扣减失败，请重试");
    }

    private Order buildOrder(Long buyerId, Product product, CreateOrderDTO dto) {
        Order order = new Order();
        order.setOrderNo(generateOrderNo(buyerId));
        order.setProductId(product.getId());
        order.setSellerId(product.getSellerId());
        order.setBuyerId(buyerId);
        order.setQuantity(dto.getQuantity());
        order.setPrice(product.getPrice());
        order.setTotalPrice(product.getPrice().multiply(BigDecimal.valueOf(dto.getQuantity())));
        order.setStatus(OrderStates.PENDING_PAYMENT.ordinal()); // 初始状态
        order.setVersion(0); // 初始版本号
        order.setReceiverName(dto.getReceiverName());
        order.setReceiverPhone(dto.getReceiverPhone());
        order.setReceiverAddress(dto.getReceiverAddress());
        order.setBuyerNote(dto.getBuyerNote());
        order.setPaymentDeadline(LocalDateTime.now().plusMinutes(30));
        return order;
    }

    private String generateOrderNo(Long buyerId) {
        String timestamp = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return String.format("XC%s%04d%d", timestamp, buyerId % 10000, random);
    }

    private void schedulePaymentTimeout(Long orderId) {
        try {
            String key = "order:payment:timeout:" + orderId;
            redisUtil.set(key, "1", 30, TimeUnit.MINUTES);
            log.info("【支付超时Key设置】orderId={}, ttl=30分钟", orderId);
        } catch (Exception e) {
            log.warn("支付超时任务设置失败: orderId={}, error={}", orderId, e.getMessage());
        }
    }

    private Order validateOrderOwnership(Long sellerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getSellerId().equals(sellerId)) {
            throw new RuntimeException("无权操作该订单");
        }
        return order;
    }

    private OrderVO convertToVO(Order order, Product product, Long buyerId) {
        User seller = userMapper.selectById(product.getSellerId());
        String sellerNickname = seller != null ? seller.getNickname() : "未知";

        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .productId(product.getId())
                .productName(product.getName())
                .productCoverImage(product.getCoverImage())
                .sellerId(product.getSellerId())
                .sellerNickname(sellerNickname)
                .buyerId(buyerId)
                .quantity(order.getQuantity())
                .totalPrice(order.getTotalPrice())
                .status(OrderStates.PENDING_PAYMENT.name())
                .receiverName(order.getReceiverName())
                .receiverPhone(order.getReceiverPhone())
                .receiverAddress(order.getReceiverAddress())
                .buyerNote(order.getBuyerNote())
                .createTime(order.getCreateTime())
                .paymentDeadline(order.getPaymentDeadline())
                .build();
    }

    /**
     * 库存回滚（取消订单时）
     */
    private void rollbackStock(Long productId, Integer quantity) {
        try {
            Product product = productMapper.selectById(productId);

            LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
            wrapper.eq(Product::getId, productId)
                    .setSql("stock = stock + {0}", quantity);

            int updateCount = productMapper.update(null, wrapper);
            if (updateCount > 0) {
                Integer newStock = product.getStock() + quantity;
                eventPublisher.publishEvent(new StockUpdatedEvent(productId, newStock));
                log.info("【库存回滚成功】productId={}, quantity={}", productId, quantity);
            }
        } catch (Exception e) {
            log.error("【库存回滚异常】productId={}, error={}", productId, e.getMessage());
        }
    }

    // ==================== 查询方法（保持不变） ====================

    @Override
    public IPage<OrderListVO> getBuyerOrders(Long buyerId, OrderQueryDTO queryDTO) {
        log.info("【查询我买到的订单】buyerId={}, pageNum={}, pageSize={}, status={}",
                buyerId, queryDTO.getPageNum(), queryDTO.getPageSize(), queryDTO.getStatus());

        try {
            Integer offset = (queryDTO.getPageNum() - 1) * queryDTO.getPageSize();

            Integer status = null;
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
                try {
                    OrderStates stateEnum = OrderStates.valueOf(queryDTO.getStatus());
                    status = stateEnum.ordinal();
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("非法的状态参数: " + queryDTO.getStatus());
                }
            }

            List<OrderListVO> records = orderMapper.selectBuyerOrdersWithDetails(
                    buyerId, status, queryDTO.getOrderNo(), queryDTO.getProductName(),
                    queryDTO.getStartTime(), queryDTO.getEndTime(),
                    queryDTO.getSortBy(), queryDTO.getAsc(),
                    offset, queryDTO.getPageSize()
            );

            Long total = orderMapper.countBuyerOrders(
                    buyerId, status, queryDTO.getOrderNo(), queryDTO.getProductName(),
                    queryDTO.getStartTime(), queryDTO.getEndTime()
            );

            IPage<OrderListVO> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                    queryDTO.getPageNum(), queryDTO.getPageSize(), total
            );
            page.setRecords(records);

            log.info("【查询成功】total={}, pages={}", total, page.getPages());
            return page;

        } catch (RuntimeException e) {
            log.warn("查询失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("查询异常: ", e);
            throw new RuntimeException("查询失败，请稍后重试");
        }
    }

    @Override
    public IPage<OrderListVO> getSellerOrders(Long sellerId, OrderQueryDTO queryDTO) {
        log.info("【查询我卖出的订单】sellerId={}, pageNum={}, pageSize={}, status={}",
                sellerId, queryDTO.getPageNum(), queryDTO.getPageSize(), queryDTO.getStatus());

        try {
            Integer offset = (queryDTO.getPageNum() - 1) * queryDTO.getPageSize();

            Integer status = null;
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
                try {
                    OrderStates stateEnum = OrderStates.valueOf(queryDTO.getStatus());
                    status = stateEnum.ordinal();
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("非法的状态参数: " + queryDTO.getStatus());
                }
            }

            List<OrderListVO> records = orderMapper.selectSellerOrdersWithDetails(
                    sellerId, status, queryDTO.getOrderNo(), queryDTO.getProductName(),
                    queryDTO.getStartTime(), queryDTO.getEndTime(),
                    queryDTO.getSortBy(), queryDTO.getAsc(),
                    offset, queryDTO.getPageSize()
            );

            Long total = orderMapper.countSellerOrders(
                    sellerId, status, queryDTO.getOrderNo(), queryDTO.getProductName(),
                    queryDTO.getStartTime(), queryDTO.getEndTime()
            );

            IPage<OrderListVO> page = new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                    queryDTO.getPageNum(), queryDTO.getPageSize(), total
            );
            page.setRecords(records);

            log.info("【查询成功】total={}, pages={}", total, page.getPages());
            return page;

        } catch (RuntimeException e) {
            log.warn("查询失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("查询异常: ", e);
            throw new RuntimeException("查询失败，请稍后重试");
        }
    }
}