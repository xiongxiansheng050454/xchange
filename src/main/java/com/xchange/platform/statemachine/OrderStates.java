package com.xchange.platform.statemachine;

/**
 * 订单状态枚举
 */
public enum OrderStates {
    // ===== 支付状态（前置流程） =====
    PENDING_PAYMENT,    // 待付款：刚下单，等待支付
    PAID,               // 已付款：支付成功

    // ===== 履约状态（后置流程） =====
    CONFIRMED,          // 已确认：卖家确认订单，准备发货
    SHIPPED,            // 已发货：卖家已发货，等待买家收货
    COMPLETED,          // 已完成：买家确认收货，交易成功

    // ===== 终态 =====
    CANCELLED,          // 已取消：订单取消（任意环节可取消）
}