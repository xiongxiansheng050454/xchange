package com.xchange.platform.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("tb_message")
public class Message {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long senderId;
    private Long receiverId;
    private Long itemId;
    private String content;
    private Integer isRead;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer deleted;
}