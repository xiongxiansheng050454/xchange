package com.xchange.platform.config;

import com.xchange.platform.statemachine.OrderEvents;
import com.xchange.platform.statemachine.OrderStateMachinePersist;
import com.xchange.platform.statemachine.OrderStates;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.statemachine.config.EnableStateMachineFactory;
import org.springframework.statemachine.config.StateMachineConfigurerAdapter;
import org.springframework.statemachine.config.builders.StateMachineStateConfigurer;
import org.springframework.statemachine.config.builders.StateMachineTransitionConfigurer;
import org.springframework.statemachine.listener.StateMachineListener;
import org.springframework.statemachine.listener.StateMachineListenerAdapter;
import org.springframework.statemachine.persist.DefaultStateMachinePersister;
import org.springframework.statemachine.persist.StateMachinePersister;
import org.springframework.statemachine.state.State;

import java.util.EnumSet;

/**
 * 订单状态机核心配置
 */
@Slf4j
@Configuration
@EnableStateMachineFactory(name = "orderStateMachineFactory")
public class OrderStateMachineConfig extends StateMachineConfigurerAdapter<OrderStates, OrderEvents> {

    /**
     * 配置状态
     */
    @Override
    public void configure(StateMachineStateConfigurer<OrderStates, OrderEvents> states) throws Exception {
        states.withStates()
                .initial(OrderStates.PENDING_PAYMENT)  // 初始状态：待付款
                .states(EnumSet.allOf(OrderStates.class))  // 所有状态
                .end(OrderStates.COMPLETED)    // 终结状态：已完成
                .end(OrderStates.CANCELLED);   // 终结状态：已取消
    }

    /**
     * 配置状态流转规则
     */
    @Override
    public void configure(StateMachineTransitionConfigurer<OrderStates, OrderEvents> transitions) throws Exception {
        transitions
                // ===== 支付流程 =====
                .withExternal().source(OrderStates.PENDING_PAYMENT).target(OrderStates.PAID)
                .event(OrderEvents.PAY).and()

                // ===== 履约流程 =====
                .withExternal().source(OrderStates.PAID).target(OrderStates.CONFIRMED)
                .event(OrderEvents.CONFIRM).and()

                .withExternal().source(OrderStates.CONFIRMED).target(OrderStates.SHIPPED)
                .event(OrderEvents.SHIP).and()

                .withExternal().source(OrderStates.SHIPPED).target(OrderStates.COMPLETED)
                .event(OrderEvents.RECEIVE).and()

                // ===== 取消流程（任意环节可取消） =====
                .withExternal().source(OrderStates.PENDING_PAYMENT).target(OrderStates.CANCELLED)
                .event(OrderEvents.CANCEL).and()
                .withExternal().source(OrderStates.PAID).target(OrderStates.CANCELLED)
                .event(OrderEvents.CANCEL).and()
                .withExternal().source(OrderStates.CONFIRMED).target(OrderStates.CANCELLED)
                .event(OrderEvents.CANCEL).and()
                .withExternal().source(OrderStates.SHIPPED).target(OrderStates.CANCELLED)
                .event(OrderEvents.CANCEL).and();
    }

    /**
     * 声明 StateMachinePersister Bean
     */
    @Bean
    public StateMachinePersister<OrderStates, OrderEvents, Long> orderStateMachinePersister(
            OrderStateMachinePersist orderStateMachinePersist) {

        // 使用默认持久化器包装我们的MySQL实现
        return new DefaultStateMachinePersister<>(orderStateMachinePersist);
    }

    /**
     * 状态机监听器：记录状态流转日志
     */
    @Bean
    public StateMachineListener<OrderStates, OrderEvents> orderStateMachineListener() {
        return new StateMachineListenerAdapter<>() {
            @Override
            public void stateChanged(State<OrderStates, OrderEvents> from, State<OrderStates, OrderEvents> to) {
                if (from != null) {
                    log.info("【订单状态流转】从 {} 变更为 {}", from.getId(), to.getId());
                } else {
                    log.info("【订单状态流转】初始化为 {}", to.getId());
                }
            }
        };
    }
}