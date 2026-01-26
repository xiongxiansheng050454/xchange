package com.xchange.platform.statemachine;

import com.xchange.platform.service.OrderStateMachineService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class OrderStateMachineTest {

    @Autowired
    private OrderStateMachineService stateMachineService;

    @Test
    public void testStateFlow() {
        Long orderId = 10001L; // 测试订单ID

        // 模拟完整流程
        System.out.println("初始状态: " + stateMachineService.getCurrentState(orderId));

        // 1. 确认订单
        boolean confirmResult = stateMachineService.sendEvent(orderId, OrderEvents.CONFIRM);
        System.out.println("确认结果: " + confirmResult);
        System.out.println("确认后状态: " + stateMachineService.getCurrentState(orderId));

        // 2. 发货
        boolean shipResult = stateMachineService.sendEvent(orderId, OrderEvents.SHIP);
        System.out.println("发货结果: " + shipResult);
        System.out.println("发货后状态: " + stateMachineService.getCurrentState(orderId));

        // 3. 确认收货
        boolean receiveResult = stateMachineService.sendEvent(orderId, OrderEvents.RECEIVE);
        System.out.println("收货结果: " + receiveResult);
        System.out.println("收货后状态: " + stateMachineService.getCurrentState(orderId));
    }

    @Test
    public void testCancelFlow() {
        Long orderId = 10002L;

        // 测试取消流程
        System.out.println("初始状态: " + stateMachineService.getCurrentState(orderId));

        // 取消订单
        boolean cancelResult = stateMachineService.sendEvent(orderId, OrderEvents.CANCEL);
        System.out.println("取消结果: " + cancelResult);
        System.out.println("取消后状态: " + stateMachineService.getCurrentState(orderId));
    }
}