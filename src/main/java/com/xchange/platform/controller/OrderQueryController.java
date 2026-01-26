package com.xchange.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.xchange.platform.common.Result;
import com.xchange.platform.dto.OrderQueryDTO;
import com.xchange.platform.service.OrderService;
import com.xchange.platform.vo.OrderListVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单查询控制器（我买到的/我卖出的）
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "订单查询", description = "分页查询我买到的和我卖出的订单")
public class OrderQueryController {

    private final OrderService orderService;

    /**
     * 我买到的订单
     * GET /api/orders/buyer
     */
    @GetMapping("/buyer")
    @Operation(summary = "我买到的订单", description = "查询当前用户作为买家的所有订单")
    public Result<IPage<OrderListVO>> getBuyerOrders(
            @RequestAttribute("userId") Long buyerId,
            @Valid OrderQueryDTO queryDTO) {

        try {
            IPage<OrderListVO> pageResult = orderService.getBuyerOrders(buyerId, queryDTO);

            // 友好的空数据提示
            if (pageResult.getTotal() == 0) {
                return Result.success("暂无订单数据", pageResult);
            }

            return Result.success("查询成功", pageResult);

        } catch (RuntimeException e) {
            log.warn("查询失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询异常: ", e);
            return Result.error(500, "系统繁忙，请稍后重试");
        }
    }

    /**
     * 我卖出的订单
     * GET /api/orders/seller
     */
    @GetMapping("/seller")
    @Operation(summary = "我卖出的订单", description = "查询当前用户作为卖家的所有订单")
    public Result<IPage<OrderListVO>> getSellerOrders(
            @RequestAttribute("userId") Long sellerId,
            @Valid OrderQueryDTO queryDTO) {

        try {
            IPage<OrderListVO> pageResult = orderService.getSellerOrders(sellerId, queryDTO);

            if (pageResult.getTotal() == 0) {
                return Result.success("暂无订单数据", pageResult);
            }

            return Result.success("查询成功", pageResult);

        } catch (RuntimeException e) {
            log.warn("查询失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询异常: ", e);
            return Result.error(500, "系统繁忙，请稍后重试");
        }
    }
}