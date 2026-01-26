package com.xchange.platform.service;

import com.xchange.platform.statemachine.OrderEvents;
import com.xchange.platform.statemachine.OrderStates;
import org.springframework.statemachine.StateMachine;

public interface OrderStateMachineService {
    /**
     * 发送事件触发状态流转
     * @param orderId 订单ID
     * @param event 触发事件
     * @return 是否成功
     */
    boolean sendEvent(Long orderId, OrderEvents event);

    /**
     * 获取订单当前状态
     * @param orderId 订单ID
     * @return 当前状态
     */
    OrderStates getCurrentState(Long orderId);

    /**
     * 初始化状态机（首次创建订单）
     */
    void initializeStateMachine(Long orderId, OrderStates initialState);
}