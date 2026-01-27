package com.xchange.platform.component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xchange.platform.entity.Product;
import com.xchange.platform.mapper.ProductMapper;
import com.xchange.platform.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockSyncJob {

    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;
    private final StockService stockService;

    /**
     * 每5分钟同步一次库存（根据业务调整频率）
     */
    @Scheduled(cron = "0 */5 * * * ?")
    public void syncStock() {
        log.info("开始库存全量同步...");

        // 1. 查询所有上架商品
        List<Product> products = productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .eq(Product::getStatus, 1)
                        .eq(Product::getDeleted, 0)
        );

        for (Product product : products) {
            String key = "stock:product:" + product.getId();
            String redisStock = redisTemplate.opsForValue().get(key);

            if (redisStock == null) {
                // Redis缺失，预热
                stockService.preloadStock(product.getId(), product.getStock());
            } else if (Integer.parseInt(redisStock) != product.getStock()) {
                // 不一致，以MySQL为准（或根据业务逻辑决定）
                log.warn("库存不一致: productId={}, MySQL={}, Redis={}",
                        product.getId(), product.getStock(), redisStock);
                stockService.preloadStock(product.getId(), product.getStock());
            }
        }

        log.info("库存同步完成，共处理{}个商品", products.size());
    }
}