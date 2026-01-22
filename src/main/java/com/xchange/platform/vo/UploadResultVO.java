package com.xchange.platform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件上传结果")
public class UploadResultVO {

    @Schema(description = "是否上传成功")
    private Boolean success;

    @Schema(description = "原文件名")
    private String originalName;

    @Schema(description = "上传后的URL")
    private String fileUrl;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "文件类型")
    private String contentType;

    @Schema(description = "上传时间")
    private LocalDateTime uploadTime;

    @Schema(description = "错误信息（上传失败时）")
    private String errorMessage;
}