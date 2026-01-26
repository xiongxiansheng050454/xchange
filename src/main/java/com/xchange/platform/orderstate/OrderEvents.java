package com.xchange.platform.orderstate;

/**
 * 订单事件枚举
 */
public enum OrderEvents {
    PAY,        // 支付成功
    CONFIRM,    // 卖家确认订单
    SHIP,       // 卖家发货
    RECEIVE,    // 买家确认收货
    CANCEL,     // 取消订单
}