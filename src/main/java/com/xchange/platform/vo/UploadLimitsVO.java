package com.xchange.platform.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "上传限制信息")
public class UploadLimitsVO {

    @Schema(description = "最大文件大小描述")
    private String maxFileSize;

    @Schema(description = "允许的文件类型列表")
    private List<String> allowedTypes;

    @Schema(description = "单次请求最多文件数")
    private Integer maxFilesPerRequest;
}