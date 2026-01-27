package com.xchange.platform.concurrent;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.xchange.platform.dto.CreateOrderDTO;
import com.xchange.platform.entity.Product;
import com.xchange.platform.mapper.ProductMapper;
import com.xchange.platform.service.OrderService;
import com.xchange.platform.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@SpringBootTest
@Slf4j
public class StockConcurrencyTest {

    // ✅ 字段注入（替代构造函数注入）
    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private RedisUtil redisUtil;

    // 线程安全的计数器
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger failCount = new AtomicInteger(0);
    private final AtomicInteger insufficientStockCount = new AtomicInteger(0);

    // 测试商品ID（需提前在数据库中存在）
    private static final Long TEST_PRODUCT_ID = 1001L;
    private static final int INITIAL_STOCK = 500;

    /**
     * 每个测试前重置数据
     */
    @BeforeEach
    public void setUp() {
        System.out.println("========== 重置测试数据 ==========");
        resetProductStock(TEST_PRODUCT_ID, INITIAL_STOCK);

        // 清空计数器
        successCount.set(0);
        failCount.set(0);
        insufficientStockCount.set(0);
    }

    /**
     * 模拟500线程并发扣库存
     */
    @Test
    public void testConcurrentStockDeduction() throws InterruptedException {
        // ========== 测试配置 ==========
        int threadCount = 500;          // 线程数
        int quantityPerOrder = 1;       // 每个订单购买数量

        // 确保库存充足
        int currentStock = getProductStock(TEST_PRODUCT_ID);
        if (currentStock < threadCount * quantityPerOrder) {
            throw new IllegalStateException(
                    String.format("库存不足: 当前库存%d，需要%d",
                            currentStock, threadCount * quantityPerOrder)
            );
        }

        // 并发控制工具
        CountDownLatch startLatch = new CountDownLatch(1);  // 同时启动
        CountDownLatch endLatch = new CountDownLatch(threadCount); // 等待结束

        System.out.println("========== 并发测试开始 ==========");
        System.out.println("线程数: " + threadCount);
        System.out.println("商品ID: " + TEST_PRODUCT_ID);
        System.out.println("商品初始库存: " + INITIAL_STOCK);
        Instant startTime = Instant.now();

        // ========== 创建线程池 ==========
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                threadCount, threadCount, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactory() {
                    private final AtomicInteger threadNumber = new AtomicInteger(1);
                    public Thread newThread(Runnable r) {
                        return new Thread(r, "Test-Thread-" + threadNumber.getAndIncrement());
                    }
                }
        );

        // ========== 提交任务 ==========
        for (int i = 0; i < threadCount; i++) {
            final long buyerId = 1000L + i; // 模拟不同用户
            executor.submit(() -> {
                try {
                    // 等待统一启动信号
                    startLatch.await();

                    // 调用下单接口
                    orderService.createOrder(buyerId,
                            CreateOrderDTO.builder()
                                    .productId(TEST_PRODUCT_ID)
                                    .quantity(quantityPerOrder)
                                    .build());

                    successCount.incrementAndGet();

                } catch (Exception e) {
                    failCount.incrementAndGet();
                    String message = e.getMessage();
                    if (message != null && message.contains("库存不足")) {
                        insufficientStockCount.incrementAndGet();
                    }
                    System.err.println("线程 " + Thread.currentThread().getName() + " 失败: " + message);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // ========== 启动所有线程 ==========
        Instant testStart = Instant.now();
        startLatch.countDown();

        // 等待所有任务完成（最多60秒超时）
        boolean completed = endLatch.await(60, TimeUnit.SECONDS);
        Instant testEnd = Instant.now();

        // ========== 关闭线程池 ==========
        executor.shutdown();
        boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);

        if (!completed) {
            System.err.println("警告: 测试超时，部分线程未完成！");
        }

        // ========== 统计结果 ==========
        Duration duration = Duration.between(testStart, testEnd);
        long actualRequests = successCount.get() + failCount.get();

        System.out.println("\n========== 测试结果 ==========");
        System.out.println("总请求数: " + actualRequests);
        System.out.println("成功数: " + successCount.get());
        System.out.println("失败数: " + failCount.get());
        System.out.println("  └─ 库存不足: " + insufficientStockCount.get());
        System.out.println("  └─ 其他异常: " + (failCount.get() - insufficientStockCount.get()));
        System.out.println("失败率: " + String.format("%.2f%%",
                (double) failCount.get() / actualRequests * 100));
        System.out.println("吞吐量: " + String.format("%.2f req/s",
                (double) actualRequests / duration.toMillis() * 1000));
        System.out.println("总耗时: " + duration.toMillis() + "ms");

        // ========== 最终库存验证 ==========
        int finalStock = getProductStock(TEST_PRODUCT_ID);
        System.out.println("\n最终库存验证: " + finalStock);
        System.out.println("预期库存: 0");

        // ========== 断言检查 ==========
        assert finalStock == 0 : "库存应为0，实际为" + finalStock;
        assert successCount.get() == INITIAL_STOCK :
                "成功订单数应为" + INITIAL_STOCK + "，实际为" + successCount.get();
        assert failCount.get() == (threadCount - INITIAL_STOCK) :
                "失败数应为" + (threadCount - INITIAL_STOCK) + "，实际为" + failCount.get();

        System.out.println("✅ 所有断言通过！");
    }

    /**
     * 辅助方法：重置商品库存
     */
    private void resetProductStock(Long productId, int stock) {
        LambdaUpdateWrapper<Product> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(Product::getId, productId)
                .set(Product::getStock, stock)
                .set(Product::getVersion, 0);

        int updated = productMapper.update(null, wrapper);
        if (updated != 1) {
            throw new RuntimeException("重置库存失败，商品ID可能不存在: " + productId);
        }

        // 同步到Redis（如果使用Redis方案）
        redisUtil.set("product:stock:" + productId, stock, 24, TimeUnit.HOURS);
        System.out.println("重置库存成功: productId=" + productId + ", stock=" + stock);
    }

    /**
     * 辅助方法：查询当前库存
     */
    private int getProductStock(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("商品不存在: " + productId);
        }
        return product.getStock();
    }
}