package com.xchange.platform.repository;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.CompletionSuggestOption;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import co.elastic.clients.elasticsearch.indices.*;
import co.elastic.clients.json.JsonData;
import com.xchange.platform.document.ProductDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 商品ES仓储类
 * 基于 Elasticsearch Java Client 8.x 原生API
 * 功能：索引管理、文档CRUD、复杂搜索、自动补全、滚动查询
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class ProductESRepository {

    private final ElasticsearchClient client;
    private static final String INDEX_NAME = "xchange_products";

    // ==================== 索引管理 ====================

    /**
     * 检查索引是否存在
     */
    public boolean indexExists() {
        try {
            return client.indices().exists(e -> e.index(INDEX_NAME)).value();
        } catch (IOException e) {
            log.error("检查索引失败: index={}, error={}", INDEX_NAME, e.getMessage());
            return false;
        }
    }

    /**
     * 创建商品索引（带IK分词器映射）
     */
    public void createIndex() {
        if (indexExists()) {
            log.info("索引已存在: {}", INDEX_NAME);
            return;
        }

        try {
            log.info("开始创建ES索引: {}", INDEX_NAME);

            // 定义映射（包含IK分词器配置）
            String mappingJson = """
                {
                    "mappings": {
                        "properties": {
                            "id": { "type": "long" },
                            "name": {
                                "type": "text",
                                "analyzer": "ik_max_word",
                                "search_analyzer": "ik_smart",
                                "fields": {
                                    "keyword": { "type": "keyword" },
                                    "suggest": {
                                        "type": "completion",
                                        "analyzer": "ik_smart"
                                    }
                                }
                            },
                            "description": {
                                "type": "text",
                                "analyzer": "ik_max_word",
                                "search_analyzer": "ik_smart"
                            },
                            "price": { "type": "double" },
                            "stock": { "type": "integer" },
                            "campusLocation": { "type": "keyword" },
                            "status": { "type": "integer" },
                            "sellerId": { "type": "long" },
                            "categoryId": { "type": "long" },
                            "categoryName": { "type": "keyword" },
                            "coverImageUrl": { "type": "keyword" },
                            "detailImageUrls": { "type": "keyword" },
                            "createTime": {
                                "type": "date",
                                "format": "yyyy-MM-dd HH:mm:ss"
                            },
                            "viewCount": { "type": "long", "index": false },
                            "tags": { "type": "keyword" },
                            "searchBoost": { "type": "integer", "index": false }
                        }
                    },
                    "settings": {
                        "number_of_shards": 1,
                        "number_of_replicas": 0,
                        "refresh_interval": "1s"
                    }
                }
                """;

            CreateIndexRequest request = CreateIndexRequest.of(b -> b
                    .index(INDEX_NAME)
                    .withJson(new java.io.StringReader(mappingJson))
            );

            CreateIndexResponse response = client.indices().create(request);
            log.info("ES索引创建成功: {}, acknowledged={}", INDEX_NAME, response.acknowledged());
        } catch (IOException e) {
            log.error("创建索引失败: {}", e.getMessage());
            throw new RuntimeException("创建ES索引失败", e);
        }
    }

    /**
     * 删除索引
     */
    public void deleteIndex() {
        try {
            DeleteIndexRequest request = DeleteIndexRequest.of(d -> d.index(INDEX_NAME));
            DeleteIndexResponse response = client.indices().delete(request);
            log.info("ES索引删除成功: {}, acknowledged={}", INDEX_NAME, response.acknowledged());
        } catch (IOException e) {
            log.error("删除索引失败: {}", e.getMessage());
            throw new RuntimeException("删除ES索引失败", e);
        }
    }

    // ==================== 文档操作 ====================

    /**
     * 保存单个文档（索引或更新）
     */
    public void save(ProductDocument document) {
        try {
            IndexRequest<ProductDocument> request = IndexRequest.of(i -> i
                    .index(INDEX_NAME)
                    .id(document.getId().toString())
                    .document(document)
            );

            IndexResponse response = client.index(request);
            log.debug("ES文档保存成功: id={}, result={}", document.getId(), response.result().jsonValue());
        } catch (IOException e) {
            log.error("ES文档保存失败: id={}, error={}", document.getId(), e.getMessage());
            throw new RuntimeException("保存ES文档失败", e);
        }
    }

    /**
     * 批量保存文档（性能优化）
     */
    public void saveAll(List<ProductDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (ProductDocument doc : documents) {
                bulkBuilder.operations(op -> op
                        .index(idx -> idx
                                .index(INDEX_NAME)
                                .id(doc.getId().toString())
                                .document(doc)
                        )
                );
            }

            BulkResponse response = client.bulk(bulkBuilder.build());

            if (response.errors()) {
                log.error("批量保存存在错误: items={}", response.items().size());
                // 记录具体错误
                for (BulkResponseItem item : response.items()) {
                    if (item.error() != null) {
                        log.error("文档错误: id={}, error={}", item.id(), item.error().reason());
                    }
                }
            } else {
                log.info("ES批量保存成功: count={}", documents.size());
            }
        } catch (IOException e) {
            log.error("ES批量保存失败: {}", e.getMessage());
            throw new RuntimeException("批量保存失败", e);
        }
    }

    /**
     * 根据ID删除文档
     */
    public void deleteById(Long productId) {
        try {
            DeleteRequest request = DeleteRequest.of(d -> d
                    .index(INDEX_NAME)
                    .id(productId.toString())
            );

            DeleteResponse response = client.delete(request);
            log.debug("ES文档删除成功: id={}, result={}", productId, response.result().jsonValue());
        } catch (IOException e) {
            log.error("ES文档删除失败: id={}, error={}", productId, e.getMessage());
        }
    }

    /**
     * 根据ID批量删除
     */
    public void deleteAllByIds(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();

            for (Long id : productIds) {
                bulkBuilder.operations(op -> op
                        .delete(d -> d
                                .index(INDEX_NAME)
                                .id(id.toString())
                        )
                );
            }

            client.bulk(bulkBuilder.build());
            log.info("ES批量删除成功: count={}", productIds.size());
        } catch (IOException e) {
            log.error("ES批量删除失败: {}", e.getMessage());
            throw new RuntimeException("批量删除失败", e);
        }
    }

    // ==================== 搜索查询 ====================

    /**
     * 复杂搜索：关键词 + 多条件筛选 + 分页 + 排序
     */
    public List<ProductDocument> searchProducts(String keyword,
                                                String campusLocation,
                                                Long categoryId,
                                                Double minPrice,
                                                Double maxPrice,
                                                Integer pageNum,
                                                Integer pageSize) {
        try {
            // 参数校验
            if (pageNum < 1) pageNum = 1;
            if (pageSize > 50) pageSize = 50; // 限制最大页大小

            log.info("ES搜索: keyword={}, campus={}, categoryId={}, price=[{}, {}], page={}/{}",
                    keyword, campusLocation, categoryId, minPrice, maxPrice, pageNum, pageSize);

            // 构建 BoolQuery
            Query query = buildBoolQuery(keyword, campusLocation, categoryId, minPrice, maxPrice);

            // 构建 SearchRequest
            Integer finalPageNum = pageNum;
            Integer finalPageSize = pageSize;
            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(query)
                    .from((finalPageNum - 1) * finalPageSize)
                    .size(finalPageSize)
                    .sort(SortOptions.of(so -> so
                            .field(f -> f
                                    .field("createTime")
                                    .order(SortOrder.Desc)
                            )
                    ))
                    .trackTotalHits(t -> t.enabled(true))  // 获取精确总数
            );

            // 执行搜索
            SearchResponse<ProductDocument> response = client.search(request, ProductDocument.class);

            // 解析结果
            List<ProductDocument> products = response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());

            // 记录统计信息
            TotalHits totalHits = response.hits().total();
            long total = totalHits != null ? totalHits.value() : 0;
            log.info("ES搜索完成: 匹配{}条, 返回{}条", total, products.size());

            return products;
        } catch (IOException e) {
            log.error("ES搜索IO异常: {}", e.getMessage());
            throw new RuntimeException("搜索失败，请稍后重试", e);
        }
    }

    /**
     * 构建 BoolQuery（组合查询）
     */
    private Query buildBoolQuery(String keyword, String campusLocation, Long categoryId,
                                 Double minPrice, Double maxPrice) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

        // 1. Must条件（关键词搜索，参与评分）
        if (StringUtils.isNotBlank(keyword)) {
            boolBuilder.must(m -> m.multiMatch(mm -> mm
                    .fields("name^2", "description")  // name字段权重设为2
                    .query(keyword)
                    .analyzer("ik_smart")
            ));
        }

        // 2. Filter条件（精确筛选，不参与评分，可缓存）
        if (StringUtils.isNotBlank(campusLocation)) {
            boolBuilder.filter(f -> f.term(t -> t
                    .field("campusLocation")
                    .value(campusLocation)
            ));
        }

        if (categoryId != null) {
            boolBuilder.filter(f -> f.term(t -> t
                    .field("categoryId")
                    .value(categoryId)
            ));
        }

        // 3. 价格范围（filter）
        if (minPrice != null || maxPrice != null) {
            boolBuilder.filter(f -> f.range(r -> {
                r.field("price");
                if (minPrice != null) r.gte(JsonData.of(minPrice));
                if (maxPrice != null) r.lte(JsonData.of(maxPrice));
                return r;
            }));
        }

        // 4. 状态筛选（只搜索上架商品）
        boolBuilder.filter(f -> f.term(t -> t
                .field("status")
                .value(1)
        ));

        return boolBuilder.build()._toQuery();
    }

    // ==================== 自动补全（搜索建议） ====================

    /**
     * 自动补全：基于completion suggester
     */
    public List<String> autoComplete(String prefix) {
        if (StringUtils.isBlank(prefix) || prefix.length() < 2) {
            return Collections.emptyList();
        }

        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .suggest(su -> su
                            .suggesters("product_suggest", field -> field
                                    .prefix(prefix)
                                    .completion(c -> c
                                            .field("name.suggest")  // 使用name字段的suggest子字段
                                            .skipDuplicates(true)
                                            .size(10)
                                    )
                            )
                    )
            );

            SearchResponse<ProductDocument> response = client.search(request, ProductDocument.class);

            // 解析建议结果
            if (response.suggest() != null) {
                return response.suggest().get("product_suggest").stream()
                        .flatMap(suggestion -> suggestion.completion().options().stream())
                        .map(CompletionSuggestOption::text)
                        .collect(Collectors.toList());
            }

            return Collections.emptyList();
        } catch (IOException e) {
            log.error("自动补全失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    // ==================== 统计与辅助 ====================

    /**
     * 获取商品总数
     */
    public long count() {
        try {
            CountRequest request = CountRequest.of(c -> c.index(INDEX_NAME));
            return client.count(request).count();
        } catch (IOException e) {
            log.error("ES计数失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 根据ID查询文档
     */
    public ProductDocument findById(Long productId) {
        try {
            GetRequest request = GetRequest.of(g -> g
                    .index(INDEX_NAME)
                    .id(productId.toString())
            );

            GetResponse<ProductDocument> response = client.get(request, ProductDocument.class);

            if (response.found()) {
                return response.source();
            }
            return null;
        } catch (IOException e) {
            log.error("ES查询失败: id={}, error={}", productId, e.getMessage());
            return null;
        }
    }

    /**
     * 判断文档是否存在
     */
    public boolean existsById(Long productId) {
        try {
            return client.exists(e -> e
                    .index(INDEX_NAME)
                    .id(productId.toString())
            ).value();
        } catch (IOException e) {
            log.error("ES存在性检查失败: id={}, error={}", productId, e.getMessage());
            return false;
        }
    }
}