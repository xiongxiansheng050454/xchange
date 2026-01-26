package com.xchange.platform.orderstate;

import com.xchange.platform.entity.Order;

/**
 * 订单状态转换器
 * 负责 Entity.status (Integer) 与 StateMachine (Enum) 互转
 */
public class OrderStateConverter {

    /**
     * Entity.status -> StateMachine Enum
     */
    public static OrderStates toStateMachineState(Integer entityStatus) {
        if (entityStatus == null) {
            return OrderStates.PENDING_PAYMENT; // 默认初始状态
        }
        return OrderStates.values()[entityStatus];
    }

    /**
     * StateMachine Enum -> Entity.status
     */
    public static Integer toEntityStatus(OrderStates stateMachineState) {
        return stateMachineState.ordinal();
    }

    /**
     * 初始化订单状态到状态机（首次）
     */
    public static void initializeOrderState(Order order) {
        order.setStatus(toEntityStatus(OrderStates.PENDING_PAYMENT));
    }
}