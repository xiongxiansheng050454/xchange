package com.xchange.platform.service.impl;

import com.xchange.platform.utils.MinioUtil;
import com.xchange.platform.vo.UploadResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 异步文件上传服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadAsyncService {

    private final MinioUtil minioUtil;

    // 允许的图片类型
    private static final List<String> ALLOWED_IMAGE_TYPES = List.of(
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    );

    // 单个文件最大大小：5MB
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 异步上传单个文件
     * @param sellerId 卖家ID
     * @param productId 商品ID
     * @param imageType 图片类型
     * @param file 文件
     * @return CompletableFuture
     */
    @Async("uploadExecutor")
    public CompletableFuture<UploadResultVO> uploadSingleFileAsync(
            Long sellerId, Long productId, String imageType, MultipartFile file) {

        log.info("[异步上传开始] sellerId={}, file={}, thread={}",
                sellerId, file.getOriginalFilename(), Thread.currentThread().getName());

        long threadId = Thread.currentThread().getId();
        String threadName = Thread.currentThread().getName();
        Instant uploadStart = Instant.now();

        try {
            // 1. 验证文件
            validateFile(file);

            // 2. 生成唯一文件名
            String objectName = generateObjectName(sellerId, productId, imageType, file.getOriginalFilename());

            // 3. 上传到MinIO
            String fileUrl = minioUtil.uploadFile(file, objectName);

            // 4. 构建成功结果
            UploadResultVO result = UploadResultVO.builder()
                    .success(true)
                    .originalName(file.getOriginalFilename())
                    .fileUrl(fileUrl)
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .uploadTime(LocalDateTime.now())
                    .errorMessage(null)
                    .build();

            log.info("[异步上传成功] sellerId={}, file={}, url={}",
                    sellerId, file.getOriginalFilename(), fileUrl);

            Duration uploadDuration = Duration.between(uploadStart, Instant.now());
            log.info("[异步线程-{}] 上传完成: file={}, 耗时={}ms",
                    threadId, file.getOriginalFilename(), uploadDuration.toMillis());

            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            // 5. 记录失败结果
            UploadResultVO result = UploadResultVO.builder()
                    .success(false)
                    .originalName(file.getOriginalFilename())
                    .fileUrl(null)
                    .fileSize(file.getSize())
                    .contentType(file.getContentType())
                    .uploadTime(LocalDateTime.now())
                    .errorMessage(e.getMessage())
                    .build();

            log.warn("[异步上传失败] sellerId={}, file={}, error={}",
                    sellerId, file.getOriginalFilename(), e.getMessage());

            Duration uploadDuration = Duration.between(uploadStart, Instant.now());
            log.warn("[异步线程-{}] 上传失败: file={}, 耗时={}ms, error={}",
                    threadId, file.getOriginalFilename(), uploadDuration.toMillis(), e.getMessage());

            return CompletableFuture.completedFuture(result);
        }
    }

    /**
     * 验证文件合法性
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }

        // 验证文件类型
        String contentType = file.getContentType();
        if (StringUtils.isBlank(contentType) || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new RuntimeException("不支持的文件类型: " + contentType + "，只允许: " + ALLOWED_IMAGE_TYPES);
        }

        // 验证文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new RuntimeException("文件大小超过限制: " + (MAX_FILE_SIZE / 1024 / 1024) + "MB");
        }
    }

    /**
     * 生成MinIO对象名称（带路径结构）
     */
    private String generateObjectName(Long sellerId, Long productId, String imageType, String originalFilename) {
        // 日期路径：2024/01/18/
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        // 文件扩展名
        String extension = "";
        if (StringUtils.isNotBlank(originalFilename) && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // 构建路径：images/{type}/2024/01/18/{sellerId}_{productId}_{uuid}.jpg
        StringBuilder objectName = new StringBuilder();
        objectName.append("images/").append(imageType).append("/")
                .append(datePath).append("/")
                .append("s").append(sellerId);

        if (productId != null) {
            objectName.append("_p").append(productId);
        }

        objectName.append("_").append(UUID.randomUUID().toString().replace("-", ""))
                .append(extension);

        return objectName.toString();
    }
}