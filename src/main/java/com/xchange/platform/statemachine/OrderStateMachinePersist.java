package com.xchange.platform.statemachine;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xchange.platform.entity.Order;
import com.xchange.platform.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachinePersist;
import org.springframework.stereotype.Component;

/**
 * 状态机持久化实现：将订单状态同步到MySQL
 */
@Slf4j
@Component  // 确保被Spring扫描
@RequiredArgsConstructor
public class OrderStateMachinePersist implements StateMachinePersist<OrderStates, OrderEvents, Long> {

    private final OrderMapper orderMapper;

    @Override
    public void write(StateMachineContext<OrderStates, OrderEvents> context, Long orderId) throws Exception {
        OrderStates currentState = context.getState();

        LambdaUpdateWrapper<Order> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Order::getId, orderId)
                .set(Order::getStatus, currentState.ordinal());

        orderMapper.update(null, wrapper);
        log.info("【状态持久化】订单{}状态已保存到数据库: {}", orderId, currentState);
    }

    @Override
    public StateMachineContext<OrderStates, OrderEvents> read(Long orderId) throws Exception {
        Order order = orderMapper.selectById(orderId);
        if (order == null) {
            return null;
        }

        OrderStates state = OrderStates.values()[order.getStatus()];

        return new org.springframework.statemachine.support.DefaultStateMachineContext<>(
                state, null, null, null, null, orderId.toString()
        );
    }
}