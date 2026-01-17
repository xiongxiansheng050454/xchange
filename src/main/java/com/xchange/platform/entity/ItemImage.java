package com.xchange.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tb_item_image")
public class ItemImage {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long itemId;
    private String imageUrl;
    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}