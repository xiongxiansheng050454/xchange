package com.xchange.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xchange.platform.entity.Order;
import com.xchange.platform.vo.OrderListVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface OrderMapper extends BaseMapper<Order> {
    /**
     * 分页查询我买到的订单（带商品信息）
     */
    List<OrderListVO> selectBuyerOrdersWithDetails(
            @Param("buyerId") Long buyerId,
            @Param("status") Integer status,
            @Param("orderNo") String orderNo,
            @Param("productName") String productName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("sortBy") String sortBy,
            @Param("asc") Boolean asc,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize);

    /**
     * 统计我买到的订单总数
     */
    Long countBuyerOrders(
            @Param("buyerId") Long buyerId,
            @Param("status") Integer status,
            @Param("orderNo") String orderNo,
            @Param("productName") String productName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    /**
     * 分页查询我卖出的订单（带商品信息）
     */
    List<OrderListVO> selectSellerOrdersWithDetails(
            @Param("sellerId") Long sellerId,
            @Param("status") Integer status,
            @Param("orderNo") String orderNo,
            @Param("productName") String productName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("sortBy") String sortBy,
            @Param("asc") Boolean asc,
            @Param("offset") Integer offset,
            @Param("pageSize") Integer pageSize);

    /**
     * 统计我卖出的订单总数
     */
    Long countSellerOrders(
            @Param("sellerId") Long sellerId,
            @Param("status") Integer status,
            @Param("orderNo") String orderNo,
            @Param("productName") String productName,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}
