package com.xchange.platform.service;

public interface StockService {
    /**
     * 预加载库存到Redis（商品上架时调用）
     */
    boolean preloadStock(Long productId, Integer stock);

    /**
     * 原子扣减库存
     * @return 剩余库存（-1:未初始化, 0:库存不足, >0:成功）
     */
    Long deductStock(Long productId, Integer quantity);

    /**
     * 回滚库存（订单取消时调用）
     */
    boolean rollbackStock(Long productId, Integer quantity);

    /**
     * 获取当前库存
     */
    Integer getStock(Long productId);
}