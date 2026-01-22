package com.xchange.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tb_product_image")
public class ProductImage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long productId;   // 商品ID
    private String imageUrl;  // 图片URL
    private Integer imageType; // 图片类型：1封面图 2详情图
    private Integer sortOrder; // 排序顺序

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}