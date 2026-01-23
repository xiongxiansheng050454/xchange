package com.xchange.platform.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xchange.platform.common.Result;
import com.xchange.platform.document.ProductDocument;
import com.xchange.platform.dto.ProductQueryDTO;
import com.xchange.platform.entity.Product;
import com.xchange.platform.repository.ProductESRepository;
import com.xchange.platform.service.ProductImageService;
import com.xchange.platform.service.ProductService;
import com.xchange.platform.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * 商品搜索控制器
 * 基于Elasticsearch实现全文搜索
 */
@Slf4j
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
@Tag(name = "商品搜索", description = "基于Elasticsearch的全文搜索接口")
public class ProductSearchController {

    private final ProductESRepository productESRepository;
    private final ProductService productService;  // 新增
    private final ProductImageService productImageService;  // 新增

    /**
     * 搜索商品
     * GET /api/search/products?keyword=手机&campus=主校区&pageNum=1&pageSize=10
     */
    @GetMapping("/products")
    @Operation(summary = "搜索商品", description = "支持关键词、校区、价格范围等条件搜索")
    public Result<List<ProductDocument>> searchProducts(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "campus", required = false) String campusLocation,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
            @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {

        try {
            log.info("商品搜索请求: keyword={}, campus={}, categoryId={}, price=[{}, {}]",
                    keyword, campusLocation, categoryId, minPrice, maxPrice);

            List<ProductDocument> products = productESRepository.searchProducts(
                    keyword, campusLocation, categoryId, minPrice, maxPrice, pageNum, pageSize
            );

            if (products.isEmpty()) {
                return Result.success("未找到相关商品", products);
            }

            log.info("搜索完成: 找到{}个商品", products.size());
            return Result.success("搜索成功", products);
        } catch (RuntimeException e) {
            log.warn("搜索失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("搜索异常: ", e);
            return Result.error(500, "搜索失败，请稍后重试");
        }
    }

    /**
     * 搜索建议（自动补全）
     * GET /api/search/suggest?prefix=苹
     */
    @GetMapping("/suggest")
    @Operation(summary = "搜索建议", description = "根据输入前缀返回商品名称建议")
    public Result<List<String>> getSearchSuggestions(@RequestParam String prefix) {
        try {
            if (StringUtils.isBlank(prefix) || prefix.length() < 2) {
                return Result.success(Collections.emptyList());
            }

            List<String> suggestions = productESRepository.autoComplete(prefix);
            return Result.success(suggestions);
        } catch (Exception e) {
            log.error("搜索建议失败: {}", e.getMessage());
            return Result.error("获取搜索建议失败");
        }
    }

    /**
     * 重建索引（用于全量同步）
     * POST /api/search/rebuild
     */
    @PostMapping("/rebuild")
    @Operation(summary = "重建索引", description = "从MySQL全量同步商品数据到ES")
    public Result<String> rebuildIndex() {
        Map<String, Object> result = new HashMap<>();

        try {
            log.info("开始重建ES商品索引...");
            Instant startTime = Instant.now();

            // 1. 删除旧索引
            productESRepository.deleteIndex();

            // 2. 创建新索引
            productESRepository.createIndex();

            // 3. 从MySQL全量同步数据
            log.info("同步MySQL数据到ES...");
            SyncResult syncResult = syncAllProductsFromMySQL();

            Duration duration = Duration.between(startTime, Instant.now());

            result.put("success", true);
            result.put("message", "索引重建完成");
            result.put("syncedProducts", syncResult.getSyncedCount());
            result.put("failedProducts", syncResult.getFailedCount());
            result.put("durationMs", duration.toMillis());

            log.info("========== 索引重建完成 ==========");
            log.info("统计: 成功同步{}个商品, 失败{}个, 总耗时{}ms",
                    syncResult.getSyncedCount(),
                    syncResult.getFailedCount(),
                    duration.toMillis());

            return Result.success("索引重建完成", result.toString());
        } catch (Exception e) {
            log.error("重建索引失败: {}", e.getMessage());
            return Result.error("重建失败: " + e.getMessage());
        }
    }

    /**
     * 从MySQL全量同步所有商品到ES
     * 采用分页处理，避免内存溢出
     */
    private SyncResult syncAllProductsFromMySQL() {
        int pageNum = 1;
        int pageSize = 100;  // 每页100条
        int totalSynced = 0;
        int totalFailed = 0;
        int totalProcessed = 0;

        while (true) {
            log.info("同步批次: pageNum={}, pageSize={}", pageNum, pageSize);

            try {
                // 1. 分页查询MySQL商品数据
                IPage<ProductVO> productPage = productService.getProductPage(
                        ProductQueryDTO.builder()
                                .pageNum(pageNum)
                                .pageSize(pageSize)
                                .build()
                );

                List<ProductVO> products = productPage.getRecords();
                if (products == null || products.isEmpty()) {
                    log.info("无更多数据，同步完成");
                    break;
                }

                log.info("查询到{}个商品", products.size());

                // 2. 转换为Document列表
                List<ProductDocument> documents = new ArrayList<>();
                for (ProductVO product : products) {
                    try {
                        // 查询商品图片
                        List<String> imageUrls = productImageService.getProductImageUrls(product.getId());
                        String coverImageUrl = productImageService.getCoverImageUrl(product.getId());

                        // 转换为Document
                        ProductDocument document = ProductDocument.builder()
                                .id(product.getId())
                                .name(product.getName())
                                .description(product.getDescription())
                                .price(product.getPrice())
                                .stock(product.getStock())
                                .campusLocation(product.getCampusLocation())
                                .status(product.getStatus())
                                .sellerId(product.getSellerId())
                                .coverImageUrl(coverImageUrl)
                                .detailImageUrls(imageUrls)
                                .createTime(product.getCreateTime())
                                .searchBoost(1)  // 默认搜索权重
                                .build();

                        documents.add(document);
                        totalProcessed++;
                    } catch (Exception e) {
                        log.error("转换商品失败: productId={}, error={}", product.getId(), e.getMessage());
                        totalFailed++;
                    }
                }

                // 7. 批量保存到ES
                if (!documents.isEmpty()) {
                    productESRepository.saveAll(documents);
                    totalSynced += documents.size();
                    log.info("批次同步成功: 本批{}个, 累计{}个", documents.size(), totalSynced);
                }

                // 8. 关键修复：判断是否有下一页（使用total）
                if (totalProcessed >= productPage.getTotal()) {
                    log.info("所有数据已处理完成: total={}", productPage.getTotal());
                    break;
                }

                pageNum++;

                // 避免ES压力
                Thread.sleep(50);


            } catch (Exception e) {
                log.error("同步批次失败: pageNum={}, error={}", pageNum, e.getMessage());
                // 继续下一批，不中断整个同步过程
                pageNum++;
            }
        }

        return new SyncResult(totalSynced, totalFailed);
    }

    /**
     * 同步结果内部类
     */
    @Data
    @AllArgsConstructor
    private static class SyncResult {
        private int syncedCount;
        private int failedCount;
    }
}