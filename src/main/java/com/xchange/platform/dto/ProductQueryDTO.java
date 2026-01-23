package com.xchange.platform.dto;

import jakarta.validation.constraints.Min;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProductQueryDTO {
    @Min(value = 1, message = "页码必须大于0")
    private Integer pageNum = 1;    // 当前页码（默认第1页）

    @Min(value = 1, message = "每页数量必须大于0")
    private Integer pageSize = 10;  // 每页数量（默认10条）

    private String name;            // 商品名称（模糊查询）
    private Integer status;         // 商品状态：1上架 0下架
    private String campusLocation;  // 校区位置
    private String sortBy;          // 排序字段（create_time、price等）
    private Boolean asc = false;    // 是否升序（默认降序，最新/最贵在前）
}