package com.xchange.platform.controller;

import com.xchange.platform.common.Result;
import com.xchange.platform.service.FileUploadService;
import com.xchange.platform.utils.MinioUtil;
import com.xchange.platform.vo.UploadLimitsVO;
import com.xchange.platform.vo.UploadResultVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

/**
 * 文件上传控制器
 * 支持批量上传图片到MinIO
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Tag(name = "文件上传", description = "图片批量上传与管理接口")
public class FileUploadController {

    private final FileUploadService fileUploadService;
    private final MinioUtil minioUtil;

    /**
     * 批量上传商品图片
     * POST /api/upload
     *
     * Content-Type: multipart/form-data
     * Authorization: Bearer <token>
     *
     * Form Data:
     * - productId: 123 (可选)
     * - imageType: detail (可选，默认为detail)
     * - files: [选择多个图片文件]
     */
    @PostMapping(consumes = "multipart/form-data")
    @Operation(summary = "异步批量上传图片", description = "并行上传多张图片，性能提升3-5倍")
    public Result<List<UploadResultVO>> uploadImagesAsync(
            @RequestAttribute("userId") Long sellerId,
            @RequestParam(value = "productId", required = false) Long productId,
            @RequestParam(value = "imageType", required = false, defaultValue = "detail") String imageType,
            @RequestParam("files") MultipartFile[] files) {

        log.info("=================== 异步批量上传开始 ===================");
        Instant startTime = Instant.now();

        // 1. 基础校验
        if (files == null || files.length == 0) {
            return Result.error("请选择至少一张图片");
        }

        if (files.length > 10) {
            return Result.error("单次上传最多10张图片");
        }

        try {
            // 2. 调用异步服务（30秒超时）
            CompletableFuture<List<UploadResultVO>> futureResult =
                    fileUploadService.uploadProductImagesAsync(sellerId, productId, imageType, List.of(files));

            // 3. 等待结果（带超时保护）
            List<UploadResultVO> results = futureResult.get(30, TimeUnit.SECONDS);

            // 4. 统计结果
            long successCount = results.stream().filter(UploadResultVO::getSuccess).count();
            long failCount = results.size() - successCount;

            // 5. 记录性能数据
            Duration duration = Duration.between(startTime, Instant.now());
            log.info("=================== 异步批量上传完成 ===================");
            log.info("总耗时: {}ms, 文件数: {}, 成功: {}, 失败: {}",
                    duration.toMillis(), files.length, successCount, failCount);
            log.info("平均每个文件耗时: {}ms", duration.toMillis() / files.length);

            String message = String.format("上传完成，成功%d张，失败%d张，耗时%dms",
                    successCount, failCount, duration.toMillis());

            return Result.success(message, results);

        } catch (TimeoutException e) {
            log.error("上传超时: sellerId={}, timeout=30s", sellerId);
            return Result.error(408, "上传超时，请减少文件数量或大小");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("上传被中断: sellerId={}", sellerId);
            return Result.error(500, "上传被中断");
        } catch (ExecutionException e) {
            log.error("上传执行异常: sellerId={}, error={}", sellerId, e.getMessage());
            return Result.error(500, "上传失败：" + e.getCause().getMessage());
        } catch (Exception e) {
            log.error("上传未知异常: sellerId={}, error={}", sellerId, e.getMessage());
            return Result.error(500, "系统繁忙，请稍后重试");
        }
    }

    /**
     * 删除图片
     * DELETE /api/upload?fileUrl=xxx
     */
    @DeleteMapping
    @Operation(summary = "删除图片", description = "根据URL删除MinIO中的图片文件")
    public Result<Void> deleteImage(
            @RequestAttribute("userId") Long sellerId,
            @RequestParam("fileUrl") String fileUrl) {

        log.info("删除图片请求: sellerId={}, fileUrl={}", sellerId, fileUrl);

        try {
            fileUploadService.deleteImage(sellerId, fileUrl);
            return Result.success("删除成功");
        } catch (RuntimeException e) {
            log.warn("删除失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("删除异常: ", e);
            return Result.error(500, "删除失败，请稍后重试");
        }
    }

    /**
     * 获取上传限制信息
     * GET /api/upload/limits
     */
    @GetMapping("/limits")
    @Operation(summary = "获取上传限制", description = "获取允许的文件类型和大小限制")
    public Result<UploadLimitsVO> getUploadLimits() {
        UploadLimitsVO limits = UploadLimitsVO.builder()
                .maxFileSize("5MB")
                .allowedTypes(List.of("jpg", "jpeg", "png", "gif", "webp"))
                .maxFilesPerRequest(10)
                .build();

        return Result.success(limits);
    }

    /**
     * MinIO健康检查
     * GET /api/upload/health
     */
    @GetMapping("/health")
    @Operation(summary = "MinIO健康检查", description = "测试MinIO服务状态")
    public Result<String> checkHealth() {
        try {
            // 简单检查是否能访问Bucket
            minioUtil.ensureBucketExists(minioUtil.getBucketName());
            return Result.success("MinIO服务正常");
        } catch (Exception e) {
            log.error("MinIO健康检查失败: {}", e.getMessage());
            return Result.error("MinIO服务异常: " + e.getMessage());
        }
    }
}