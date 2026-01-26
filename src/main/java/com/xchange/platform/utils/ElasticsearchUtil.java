package com.xchange.platform.utils;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.*;
import com.xchange.platform.vo.ProductVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Elasticsearch 工具类
 * 封装常用操作：索引管理、文档CRUD、搜索
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchUtil {

    private final ElasticsearchClient client;
    private static final String PRODUCT_INDEX = "xchange_products";

    /**
     * 更新商品库存（即时同步 - 安全版）
     * @param productId 商品ID
     * @param newStock 新库存
     */
    public void updateProductStock(Long productId, Integer newStock) {
        try {
            log.info("【ES库存同步开始】productId={}, newStock={}", productId, newStock);

            // 1. 构建更新Map（只包含需要更新的字段）
            Map<String, Object> updateDoc = new HashMap<>();
            updateDoc.put("stock", newStock);
            updateDoc.put("updateTime", java.time.LocalDateTime.now());

            // 2. 执行更新（使用Refresh.WaitFor确保可见性）
            UpdateResponse<Map> response = client.update(u -> u
                            .index(PRODUCT_INDEX)
                            .id(productId.toString())
                            .doc(updateDoc)
                            .retryOnConflict(5) // 增加到5次重试
                            .refresh(Refresh.WaitFor),
                    Map.class // 使用Map.class避免反序列化问题
            );

            log.info("【ES库存同步成功】productId={}, newStock={}, result={}, version={}",
                    productId, newStock, response.result().jsonValue(), response.version());

        } catch (Exception e) {
            log.error("【ES库存同步异常】productId={}, error={}", productId, e.getMessage(), e);
        }
    }

    /**
     * 检查索引是否存在
     */
    public boolean indexExists(String indexName) {
        try {
            return client.indices().exists(c -> c.index(indexName)).value();
        } catch (IOException e) {
            log.error("检查索引失败: index={}, error={}", indexName, e.getMessage());
            return false;
        }
    }

    /**
     * 创建商品索引（带映射）
     */
    public void createProductIndex() {
        try {
            if (indexExists(PRODUCT_INDEX)) {
                log.info("索引已存在: {}", PRODUCT_INDEX);
                return;
            }

            CreateIndexRequest request = CreateIndexRequest.of(b -> b
                    .index(PRODUCT_INDEX)
                    .mappings(m -> m
                            .properties("id", p -> p.long_(l -> l))
                            .properties("name", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("description", p -> p.text(t -> t.analyzer("ik_max_word").searchAnalyzer("ik_smart")))
                            .properties("price", p -> p.double_(d -> d))
                            .properties("stock", p -> p.integer(i -> i))
                            .properties("status", p -> p.integer(i -> i))
                            .properties("campusLocation", p -> p.keyword(k -> k))
                            .properties("sellerId", p -> p.long_(l -> l))
                            .properties("coverImageUrl", p -> p.keyword(k -> k))
                            .properties("detailImageUrls", p -> p.keyword(k -> k))
                            .properties("createTime", p -> p.date(d -> d))
                    )
                    .settings(s -> s
                            .numberOfShards("1")
                            .numberOfReplicas("0")
                    )
            );

            CreateIndexResponse response = client.indices().create(request);
            log.info("创建索引成功: {}, acknowledged={}", PRODUCT_INDEX, response.acknowledged());
        } catch (IOException e) {
            log.error("创建索引失败: {}", e.getMessage());
            throw new RuntimeException("创建索引失败", e);
        }
    }

    /**
     * 删除索引
     */
    public void deleteIndex(String indexName) {
        try {
            DeleteIndexResponse response = client.indices().delete(d -> d.index(indexName));
            log.info("删除索引成功: {}, acknowledged={}", indexName, response.acknowledged());
        } catch (IOException e) {
            log.error("删除索引失败: {}", e.getMessage());
            throw new RuntimeException("删除索引失败", e);
        }
    }

    /**
     * 添加/更新商品文档
     */
    public void saveProductDocument(ProductVO product) {
        try {
            IndexRequest<ProductVO> request = IndexRequest.of(i -> i
                    .index(PRODUCT_INDEX)
                    .id(product.getId().toString())
                    .document(product)
            );

            IndexResponse response = client.index(request);
            log.debug("保存商品文档成功: id={}, result={}", product.getId(), response.result().jsonValue());
        } catch (IOException e) {
            log.error("保存商品文档失败: id={}, error={}", product.getId(), e.getMessage());
            throw new RuntimeException("保存商品文档失败", e);
        }
    }

    /**
     * 批量保存商品文档
     */
    public void batchSaveProductDocuments(List<ProductVO> products) {
        if (products == null || products.isEmpty()) {
            return;
        }

        try {
            List<BulkOperation> operations = products.stream()
                    .map(product -> BulkOperation.of(o -> o
                            .index(i -> i
                                    .index(PRODUCT_INDEX)
                                    .id(product.getId().toString())
                                    .document(product)
                            )
                    ))
                    .collect(Collectors.toList());

            BulkRequest request = BulkRequest.of(b -> b.operations(operations));
            BulkResponse response = client.bulk(request);

            if (response.errors()) {
                log.error("批量保存存在错误: items={}", response.items().size());
                // 可以记录具体错误
                response.items().stream()
                        .filter(item -> item.error() != null)
                        .forEach(item -> log.error("文档错误: id={}, error={}",
                                item.id(), item.error().reason()));
            } else {
                log.info("批量保存成功: count={}", products.size());
            }
        } catch (IOException e) {
            log.error("批量保存失败: {}", e.getMessage());
            throw new RuntimeException("批量保存失败", e);
        }
    }

    /**
     * 根据ID删除商品文档
     */
    public void deleteProductDocument(Long productId) {
        try {
            DeleteRequest request = DeleteRequest.of(d -> d
                    .index(PRODUCT_INDEX)
                    .id(productId.toString())
            );

            DeleteResponse response = client.delete(request);
            log.debug("删除商品文档成功: id={}, result={}", productId, response.result().jsonValue());
        } catch (IOException e) {
            log.error("删除商品文档失败: id={}, error={}", productId, e.getMessage());
            // 不抛出异常，因为可能是文档不存在
        }
    }

    /**
     * 搜索商品（支持关键词、校区筛选、分页）
     */
    public List<ProductVO> searchProducts(String keyword, String campusLocation, Integer pageNum, Integer pageSize) {
        try {
            // 使用Query.of()构建
            Query query = buildSearchQuery(keyword, campusLocation);

            SearchRequest request = SearchRequest.of(s -> s
                    .index(PRODUCT_INDEX)
                    .query(query)
                    .from((pageNum - 1) * pageSize)
                    .size(pageSize)
                    .sort(sort -> sort.field(f -> f.field("createTime").order(SortOrder.Desc)))
            );

            SearchResponse<ProductVO> response = client.search(request, ProductVO.class);

            List<ProductVO> products = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("搜索完成: keyword={}, campus={}, hits={}", keyword, campusLocation, products.size());
            return products;
        } catch (IOException e) {
            log.error("搜索失败: {}", e.getMessage(), e);
            throw new RuntimeException("搜索失败", e);
        }
    }

    /**
     * 构建搜索查询
     */
    private Query buildSearchQuery(String keyword, String campusLocation) {
        // 构建 BoolQuery
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 1. 添加 must 条件（关键词搜索）
        if (StringUtils.isNotBlank(keyword)) {
            boolBuilder.must(must -> must
                    .multiMatch(mm -> mm
                            .fields("name", "description")
                            .query(keyword)
                            .type(TextQueryType.BestFields)
                    )
            );
        } else {
            boolBuilder.must(must -> must.matchAll(ma -> ma));
        }

        // 2. 添加 filter 条件（校区筛选）
        if (StringUtils.isNotBlank(campusLocation)) {
            boolBuilder.filter(filter -> filter
                    .term(t -> t
                            .field("campusLocation")
                            .value(campusLocation)
                    )
            );
        }

        // 3. 构建并返回 Query
        return boolBuilder.build()._toQuery();
    }
}