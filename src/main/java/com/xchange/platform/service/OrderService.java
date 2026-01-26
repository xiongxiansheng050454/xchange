package com.xchange.platform.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xchange.platform.dto.CreateOrderDTO;
import com.xchange.platform.dto.OrderQueryDTO;
import com.xchange.platform.statemachine.OrderStates;
import com.xchange.platform.vo.OrderListVO;
import com.xchange.platform.vo.OrderVO;

public interface OrderService {

    /**
     * 买家支付成功（PENDING_PAYMENT → PAID → PENDING）
     */
    void handlePaymentSuccess(Long orderId, String paymentId);

    /**
     * 卖家确认订单（PENDING → CONFIRMED）
     */
    void confirmOrder(Long sellerId, Long orderId);

    /**
     * 卖家发货（CONFIRMED → SHIPPED）
     */
    void shipOrder(Long sellerId, Long orderId, String trackingNumber);

    /**
     * 买家确认收货（SHIPPED → COMPLETED）
     */
    void receiveOrder(Long buyerId, Long orderId);

    /**
     * 取消订单（任意状态 → CANCELLED）
     */
    void cancelOrder(Long userId, Long orderId);

    /**
     * 获取订单当前状态
     */
    OrderStates getCurrentState(Long orderId);

    /**
     * 创建订单
     */
    OrderVO createOrder(Long buyerId, CreateOrderDTO createOrderDTO);

    /**
     * 分页查询我买到的订单
     * @param buyerId 买家ID（从JWT获取）
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    IPage<OrderListVO> getBuyerOrders(Long buyerId, OrderQueryDTO queryDTO);

    /**
     * 分页查询我卖出的订单
     * @param sellerId 卖家ID（从JWT获取）
     * @param queryDTO 查询条件
     * @return 分页结果
     */
    IPage<OrderListVO> getSellerOrders(Long sellerId, OrderQueryDTO queryDTO);
}