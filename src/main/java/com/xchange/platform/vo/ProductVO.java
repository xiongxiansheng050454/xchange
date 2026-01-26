package com.xchange.platform.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "商品视图对象")
@JsonIgnoreProperties(ignoreUnknown = true) // 忽略未知字段
@JsonInclude(JsonInclude.Include.NON_NULL) // 不序列化null值
public class ProductVO {
    @Schema(description = "商品ID")
    private Long id;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "商品描述")
    private String description;

    @Schema(description = "价格")
    private BigDecimal price;

    @Schema(description = "库存")
    private Integer stock;

    @Schema(description = "校区位置")
    private String campusLocation;

    @Schema(description = "商品状态：1上架 0下架")
    private Integer status;

    @Schema(description = "卖家ID")
    private Long sellerId;

    @Schema(description = "封面图URL")
    private String coverImageUrl;

    @Schema(description = "详情图URL列表")
    private List<String> detailImageUrls;

    @Schema(description = "创建时间")
    @JsonProperty("createTime")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;

    // 添加版本号字段（ES内部版本）
    @JsonProperty("_version")
    private Long version;
}