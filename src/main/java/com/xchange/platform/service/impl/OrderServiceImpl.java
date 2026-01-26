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
import com.xchange.platform.service.OrderService;
import com.xchange.platform.service.OrderStateMachineService;
import com.xchange.platform.statemachine.OrderEvents;
import com.xchange.platform.statemachine.OrderStateConverter;
import com.xchange.platform.statemachine.OrderStates;
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
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单服务实现（状态机驱动版）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final UserMapper userMapper;
    private final OrderStateMachineService stateMachineService;
    private final RedisUtil redisUtil;
    private final ApplicationEventPublisher eventPublisher;

    // ==================== 核心接口：下单 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long buyerId, CreateOrderDTO createOrderDTO) {
        log.info("【状态机驱动-下单开始】buyerId={}, productId={}, quantity={}",
                buyerId, createOrderDTO.getProductId(), createOrderDTO.getQuantity());

        long startTime = System.currentTimeMillis();

        try {
            // 1. 参数校验
            validateCreateOrderDTO(createOrderDTO);

            // 2. 查询商品（无锁）
            Product product = productMapper.selectById(createOrderDTO.getProductId());
            if (product == null) {
                throw new RuntimeException("商品不存在或已下架");
            }
            validateProduct(product, createOrderDTO.getQuantity());

            // 3. 乐观锁扣减库存（带重试）
            deductStockWithOptimisticLock(product.getId(), createOrderDTO.getQuantity());

            // 4. 生成订单（初始状态：待付款）
            Order order = buildOrder(buyerId, product, createOrderDTO);
            OrderStateConverter.initializeOrderState(order); // 状态机初始化
            orderMapper.insert(order);

            // 5. 状态机持久化（首次）
            stateMachineService.initializeStateMachine(order.getId(), OrderStates.PENDING_PAYMENT);

            // 6. 设置支付超时任务（30分钟）
            schedulePaymentTimeout(order.getId());

            log.info("【状态机驱动-下单成功】orderId={}, orderNo={}, status={}, costTime={}ms",
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

    /**
     * 支付成功回调（由支付服务调用）
     * @param orderId 订单ID
     * @param paymentId 支付流水号
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handlePaymentSuccess(Long orderId, String paymentId) {
        log.info("【支付成功回调】orderId={}, paymentId={}", orderId, paymentId);

        try {
            // 查询订单
            Order order = orderMapper.selectById(orderId);
            if (order == null) {
                throw new RuntimeException("订单不存在");
            }

            // 幂等检查：只有待付款状态才处理
            OrderStates currentState = stateMachineService.getCurrentState(orderId);
            if (currentState != OrderStates.PENDING_PAYMENT) {
                log.warn("【支付回调幂等】订单状态不是待付款，跳过处理: orderId={}, status={}",
                        orderId, currentState);
                return;
            }

            // 驱动状态机流转：待付款 -> 待确认
            boolean result = stateMachineService.sendEvent(orderId, OrderEvents.PAY);
            if (!result) {
                throw new RuntimeException("支付后状态流转失败，请检查状态机配置");
            }

            // 清理支付超时Key
            redisUtil.delete("order:payment:timeout:" + orderId);

            log.info("【支付成功-状态流转】orderId={}, paymentId={}, newStatus={}",
                    orderId, paymentId, OrderStates.PAID);

        } catch (RuntimeException e) {
            log.error("【支付回调失败】orderId={}, error={}", orderId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("【支付回调异常】orderId={}, error={}", orderId, e.getMessage(), e);
            throw new RuntimeException("支付回调处理失败");
        }
    }

    // ==================== 履约流程 ====================

    @Override
    public void confirmOrder(Long sellerId, Long orderId) {
        log.info("【卖家确认订单】sellerId={}, orderId={}", sellerId, orderId);

        // 1. 校验订单归属权
        validateOrderOwnership(sellerId, orderId);

        // 2. 检查当前状态
        OrderStates currentState = stateMachineService.getCurrentState(orderId);
        if (currentState != OrderStates.PAID) {
            throw new RuntimeException("只有已付款订单才能确认，当前状态: " + currentState);
        }

        // 3. 驱动状态机流转
        boolean result = stateMachineService.sendEvent(orderId, OrderEvents.CONFIRM);
        if (!result) {
            throw new RuntimeException("订单确认失败，状态流转被拒绝");
        }

        log.info("【订单确认成功】orderId={}, sellerId={}", orderId, sellerId);
    }

    @Override
    public void shipOrder(Long sellerId, Long orderId, String trackingNumber) {
        log.info("【卖家发货】sellerId={}, orderId={}, tracking={}", sellerId, orderId, trackingNumber);

        // 1. 校验
        validateOrderOwnership(sellerId, orderId);
        OrderStates currentState = stateMachineService.getCurrentState(orderId);
        if (currentState != OrderStates.CONFIRMED) {
            throw new RuntimeException("只有已确认订单才能发货，当前状态: " + currentState);
        }

        // 2. 更新物流信息（示例）
        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId)
                .set(Order::getTrackingNumber, trackingNumber); // 假设Order有trackingNumber字段
        orderMapper.update(null, wrapper);

        // 3. 驱动状态机
        boolean result = stateMachineService.sendEvent(orderId, OrderEvents.SHIP);
        if (!result) {
            throw new RuntimeException("发货失败，状态流转被拒绝");
        }

        log.info("【发货成功】orderId={}, sellerId={}, tracking={}", orderId, sellerId, trackingNumber);
    }

    @Override
    public void receiveOrder(Long buyerId, Long orderId) {
        log.info("【买家确认收货】buyerId={}, orderId={}", buyerId, orderId);

        // 1. 校验买家身份
        Order order = orderMapper.selectById(orderId);
        if (order == null || !order.getBuyerId().equals(buyerId)) {
            throw new RuntimeException("无权操作该订单");
        }

        // 2. 检查状态
        OrderStates currentState = stateMachineService.getCurrentState(orderId);
        if (currentState != OrderStates.SHIPPED) {
            throw new RuntimeException("只有已发货订单才能确认收货，当前状态: " + currentState);
        }

        // 3. 驱动状态机
        boolean result = stateMachineService.sendEvent(orderId, OrderEvents.RECEIVE);
        if (!result) {
            throw new RuntimeException("确认收货失败，状态流转被拒绝");
        }

        log.info("【交易完成】orderId={}, buyerId={}", orderId, buyerId);
    }

    @Override
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
        OrderStates currentState = stateMachineService.getCurrentState(orderId);
        if (currentState == OrderStates.COMPLETED || currentState == OrderStates.CANCELLED) {
            throw new RuntimeException("已完成或已取消的订单无法再次取消");
        }

        // 3. 驱动状态机
        boolean result = stateMachineService.sendEvent(orderId, OrderEvents.CANCEL);
        if (!result) {
            throw new RuntimeException("取消订单失败，状态流转被拒绝");
        }

        // 4. 回滚库存
        rollbackStock(order.getProductId(), order.getQuantity());

        log.info("【订单已取消】orderId={}, userId={}, oldStatus={}", orderId, userId, currentState);
    }

    @Override
    public OrderStates getCurrentState(Long orderId) {
        return stateMachineService.getCurrentState(orderId);
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

                // 异步发布事件
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
        order.setStatus(OrderStateConverter.toEntityStatus(OrderStates.PENDING_PAYMENT));
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
            redisUtil.set(key, "1", 30, java.util.concurrent.TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("支付超时任务设置失败: orderId={}, error={}", orderId, e.getMessage());
        }
    }

    private void validateOrderOwnership(Long sellerId, Long orderId) {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            throw new RuntimeException("订单不存在");
        }
        if (!order.getSellerId().equals(sellerId)) {
            throw new RuntimeException("无权操作该订单");
        }
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

    @Override
    public IPage<OrderListVO> getBuyerOrders(Long buyerId, OrderQueryDTO queryDTO) {
        log.info("【查询我买到的订单】buyerId={}, pageNum={}, pageSize={}, status={}",
                buyerId, queryDTO.getPageNum(), queryDTO.getPageSize(), queryDTO.getStatus());

        try {
            // 1. 计算offset
            Integer offset = (queryDTO.getPageNum() - 1) * queryDTO.getPageSize();

            // 2. 转换状态（String -> Integer）
            Integer status = null;
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().isEmpty()) {
                try {
                    OrderStates stateEnum = OrderStates.valueOf(queryDTO.getStatus());
                    status = stateEnum.ordinal();
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("非法的状态参数: " + queryDTO.getStatus());
                }
            }

            // 3. 查询列表
            List<OrderListVO> records = orderMapper.selectBuyerOrdersWithDetails(
                    buyerId, status, queryDTO.getOrderNo(), queryDTO.getProductName(),
                    queryDTO.getStartTime(), queryDTO.getEndTime(),
                    queryDTO.getSortBy(), queryDTO.getAsc(),
                    offset, queryDTO.getPageSize()
            );

            // 4. 查询总数
            Long total = orderMapper.countBuyerOrders(
                    buyerId, status, queryDTO.getOrderNo(), queryDTO.getProductName(),
                    queryDTO.getStartTime(), queryDTO.getEndTime()
            );

            // 5. 构建分页对象
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