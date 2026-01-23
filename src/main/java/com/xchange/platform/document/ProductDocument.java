package com.xchange.platform.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品ES文档实体
 * 映射到 Elasticsearch 索引
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "xchange_products")
@Setting(shards = 1, replicas = 0)
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知字段
public class ProductDocument {

    @Id
    @Field(type = FieldType.Long)
    private Long id;  // 商品ID

    @Field(type = FieldType.Text,
            analyzer = "ik_max_word",          // 索引时使用ik_max_word分词
            searchAnalyzer = "ik_smart")       // 搜索时使用ik_smart分词
    private String name;  // 商品名称

    @Field(type = FieldType.Text,
            analyzer = "ik_max_word",
            searchAnalyzer = "ik_smart")
    private String description;  // 商品描述

    @Field(type = FieldType.Double)
    private BigDecimal price;  // 价格

    @Field(type = FieldType.Integer)
    private Integer stock;  // 库存

    @Field(type = FieldType.Keyword)
    private String campusLocation;  // 校区位置

    @Field(type = FieldType.Integer)
    private Integer status;  // 状态：1上架 0下架

    @Field(type = FieldType.Long)
    private Long sellerId;  // 卖家ID

    @Field(type = FieldType.Keyword)
    private String categoryName;  // 分类名称

    @Field(type = FieldType.Long)
    private Long categoryId;  // 分类ID

    @Field(type = FieldType.Keyword)
    private String coverImageUrl;  // 封面图URL

    @Field(type = FieldType.Keyword)
    private List<String> detailImageUrls;  // 详情图URL列表

    @Field(type = FieldType.Date, format = DateFormat.date_hour_minute_second)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;  // 创建时间

    @Field(type = FieldType.Long)
    private Long viewCount;  // 浏览量

    @Field(type = FieldType.Keyword)
    private List<String> tags;  // 商品标签（用于搜索优化）

    /**
     * 搜索权重字段
     */
    @Field(type = FieldType.Long, index = false)  // 不索引，仅存储
    private Integer searchBoost;  // 搜索权重（数值越高排名越靠前）
}