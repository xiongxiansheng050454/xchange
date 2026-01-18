package com.xchange.platform.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class ProductDTO {
    @NotBlank(message = "商品名称不能为空")
    @Size(min = 2, max = 100, message = "商品名称长度2-100位")
    private String name;

    @NotBlank(message = "商品描述不能为空")
    @Size(min = 10, max = 2000, message = "商品描述长度10-2000位")
    private String description;

    @NotNull(message = "商品价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    @Digits(integer = 10, fraction = 2, message = "价格格式错误")
    private BigDecimal price;

    @NotNull(message = "库存数量不能为空")
    @Min(value = 0, message = "库存不能小于0")
    @Max(value = 9999, message = "库存不能大于9999")
    private Integer stock;

    @NotBlank(message = "封面图不能为空")
    @Pattern(regexp = "^https?://.+", message = "封面图URL格式不正确")
    private String coverImage;

    private String campusLocation; // 校区位置
}