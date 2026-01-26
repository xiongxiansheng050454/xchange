package com.xchange.platform.controller;

import com.xchange.platform.common.Result;
import com.xchange.platform.dto.CreateOrderDTO;
import com.xchange.platform.service.OrderService;
import com.xchange.platform.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "订单管理", description = "订单状态流转接口（状态机驱动）")
public class OrderController {

    private final OrderService orderService;

    /**
     * 卖家确认订单
     * POST /api/orders/{orderId}/confirm
     */
    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "卖家确认订单", description = "将订单从PENDING转为CONFIRMED")
    public Result<Void> confirmOrder(
            @RequestAttribute("userId") Long sellerId,
            @PathVariable Long orderId) {

        try {
            orderService.confirmOrder(sellerId, orderId);
            return Result.success("订单确认成功");
        } catch (RuntimeException e) {
            log.warn("订单确认失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 卖家发货
     * POST /api/orders/{orderId}/ship
     */
    @PostMapping("/{orderId}/ship")
    @Operation(summary = "卖家发货", description = "将订单从CONFIRMED转为SHIPPED")
    public Result<Void> shipOrder(
            @RequestAttribute("userId") Long sellerId,
            @PathVariable Long orderId,
            @RequestParam(required = false) String trackingNumber) {

        try {
            orderService.shipOrder(sellerId, orderId, trackingNumber);
            return Result.success("发货成功");
        } catch (RuntimeException e) {
            log.warn("发货失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 买家确认收货
     * POST /api/orders/{orderId}/receive
     */
    @PostMapping("/{orderId}/receive")
    @Operation(summary = "买家确认收货", description = "将订单从SHIPPED转为COMPLETED")
    public Result<Void> receiveOrder(
            @RequestAttribute("userId") Long buyerId,
            @PathVariable Long orderId) {

        try {
            orderService.receiveOrder(buyerId, orderId);
            return Result.success("确认收货成功，交易完成");
        } catch (RuntimeException e) {
            log.warn("确认收货失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 取消订单
     * POST /api/orders/{orderId}/cancel
     */
    @PostMapping("/{orderId}/cancel")
    @Operation(summary = "取消订单", description = "将订单转为CANCELLED状态")
    public Result<Void> cancelOrder(
            @RequestAttribute("userId") Long userId,
            @PathVariable Long orderId) {

        try {
            orderService.cancelOrder(userId, orderId);
            return Result.success("订单已取消");
        } catch (RuntimeException e) {
            log.warn("取消订单失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询订单当前状态
     * GET /api/orders/{orderId}/status
     */
    @GetMapping("/{orderId}/status")
    @Operation(summary = "查询订单状态", description = "获取订单当前状态机状态")
    public Result<String> getOrderStatus(@PathVariable Long orderId) {
        try {
            String status = orderService.getCurrentState(orderId).name();
            return Result.success("查询成功", status);
        } catch (Exception e) {
            log.error("查询订单状态失败: {}", e.getMessage());
            return Result.error("查询失败");
        }
    }

    /**
     * 下单接口（创建订单）
     * POST /api/orders
     */
    @PostMapping
    @Operation(summary = "创建订单", description = "下单并生成待付款订单，30分钟内未支付自动取消")
    public Result<OrderVO> createOrder(
            @RequestAttribute("userId") Long buyerId,
            @Valid @RequestBody CreateOrderDTO createOrderDTO) {

        log.info("【下单请求】buyerId={}, productId={}, quantity={}",
                buyerId, createOrderDTO.getProductId(), createOrderDTO.getQuantity());

        try {
            OrderVO orderVO = orderService.createOrder(buyerId, createOrderDTO);
            return Result.success("下单成功，请在30分钟内完成支付", orderVO);
        } catch (RuntimeException e) {
            log.warn("下单失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("下单异常: ", e);
            return Result.error(500, "系统繁忙，请稍后重试");
        }
    }

    /**
     * 支付成功回调（模拟）
     * POST /api/orders/{orderId}/pay
     */
    @PostMapping("/{orderId}/pay")
    @Operation(summary = "支付成功回调", description = "订单从待付款流转到已付款")
    public Result<Void> handlePaymentSuccess(
            @PathVariable Long orderId,
            @RequestParam String paymentId) {

        try {
            orderService.handlePaymentSuccess(orderId, paymentId);
            return Result.success("支付成功，订单状态已更新");
        } catch (RuntimeException e) {
            log.warn("支付回调失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("支付回调异常: ", e);
            return Result.error(500, "支付回调处理失败");
        }
    }
}