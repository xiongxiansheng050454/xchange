package com.xchange.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@TableName("tb_product")
public class Product {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;           // 商品名称
    private String description;    // 商品描述
    private BigDecimal price;      // 售价
    private Integer stock;         // 库存数量
    private String coverImage;     // 封面图URL
    private Integer status;        // 状态：1上架 0下架
    private String campusLocation; // 校区位置
    private Long sellerId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;       // 逻辑删除
}