package com.xchange.platform.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;

@Data
@Schema(description = "批量上传图片请求")
public class FileUploadDTO {

    @Schema(description = "商品ID（可选，用于关联图片）")
    private Long productId;

    @Schema(description = "图片类型：cover(封面图)、detail(详情图)、avatar(头像)")
    private String imageType = "detail";

    @NotNull(message = "请至少上传一张图片")
    @Schema(description = "图片文件列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<MultipartFile> files;
}