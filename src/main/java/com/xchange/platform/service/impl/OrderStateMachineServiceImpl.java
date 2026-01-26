package com.xchange.platform.service.impl;

import com.xchange.platform.statemachine.*;
import com.xchange.platform.service.OrderStateMachineService;
import com.xchange.platform.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.StateMachineContext;
import org.springframework.statemachine.StateMachineEventResult;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.support.DefaultStateMachineContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.concurrent.TimeUnit;

/**
 * 订单状态机业务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStateMachineServiceImpl implements OrderStateMachineService {

    private final StateMachineFactory<OrderStates, OrderEvents> orderStateMachineFactory;
    private final StateMachinePersister<OrderStates, OrderEvents, Long> orderStateMachinePersister;
    private final RedisUtil redisUtil;

    private final StateMachineFactory<OrderStates, OrderEvents> stateMachineFactory;
    private final StateMachinePersister<OrderStates, OrderEvents, Long> persister;
    /**
     * 初始化状态机（首次创建订单）
     */
    @Override
    public void initializeStateMachine(Long orderId, OrderStates initialState) {
        StateMachine<OrderStates, OrderEvents> stateMachine = stateMachineFactory.getStateMachine();

        try {
            // 1. 启动状态机
            stateMachine.startReactively().block();

            // 2. 设置初始状态
            stateMachine.getStateMachineAccessor()
                    .doWithAllRegions(accessor -> {
                        StateMachineContext<OrderStates, OrderEvents> context =
                                new DefaultStateMachineContext<>(initialState, null, null, null, null, orderId.toString());
                        accessor.resetStateMachineReactively(context).block(); // 响应式重置
                    });

            // 3. 持久化到Redis（用于后续事件驱动）
            persister.persist(stateMachine, orderId);

            log.info("【状态机初始化成功】orderId={}, initialState={}", orderId, initialState);

        } catch (Exception e) {
            log.error("【状态机初始化失败】orderId={}, error={}", orderId, e.getMessage());
            throw new RuntimeException("状态机初始化失败", e);
        } finally {
            stateMachine.stopReactively().block();
        }
    }

    @Override
    public boolean sendEvent(Long orderId, OrderEvents event) {
        // 每次获取新的状态机实例
        StateMachine<OrderStates, OrderEvents> stateMachine = orderStateMachineFactory.getStateMachine();

        try {
            // 恢复状态
            orderStateMachinePersister.restore(stateMachine, orderId);

            // 发送事件
            Message<OrderEvents> message = MessageBuilder
                    .withPayload(event)
                    .setHeader("orderId", orderId)
                    .build();

            StateMachineEventResult<OrderStates, OrderEvents> result =
                    stateMachine.sendEvent(Mono.just(message)).blockFirst();

            //判断事件是否被接受
            boolean success = result != null &&
                    result.getResultType() == StateMachineEventResult.ResultType.ACCEPTED;

            if (success) {
                // 持久化新状态
                orderStateMachinePersister.persist(stateMachine, orderId);
                logStateTransition(orderId, stateMachine.getState().getId(), event);
            } else {
                log.warn("【状态机事件被拒绝】订单{}事件{}未被接受", orderId, event);
            }

            return success;

        } catch (Exception e) {
            log.error("【状态机事件失败】订单{}事件{}异常: {}", orderId, event, e.getMessage());
            return false;
        } finally {
            stateMachine.stopReactively().block();
        }
    }

    @Override
    public OrderStates getCurrentState(Long orderId) {
        StateMachine<OrderStates, OrderEvents> stateMachine = orderStateMachineFactory.getStateMachine();
        try {
            orderStateMachinePersister.restore(stateMachine, orderId);
            return stateMachine.getState().getId();
        } catch (Exception e) {
            log.error("获取订单状态失败: orderId={}", orderId);
            return null;
        } finally {
            stateMachine.stopReactively().block();
        }
    }

    /**
     * 记录状态流转日志到Redis（保留7天）
     */
    private void logStateTransition(Long orderId, OrderStates newState, OrderEvents event) {
        try {
            String key = "order:state:log:" + orderId;
            String logEntry = String.format("%d:%s:%s:%d",
                    System.currentTimeMillis(),
                    event.name(),
                    newState.name(),
                    Thread.currentThread().getId()
            );

            redisUtil.lRightPush(key, logEntry);
            redisUtil.expire(key, 7, TimeUnit.DAYS);

            log.debug("【Redis日志】订单{}状态流转已记录", orderId);
        } catch (Exception e) {
            log.warn("状态流转日志记录失败: {}", e.getMessage());
        }
    }
}