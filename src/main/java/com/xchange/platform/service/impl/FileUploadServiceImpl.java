package com.xchange.platform.service.impl;

import com.xchange.platform.service.FileUploadService;
import com.xchange.platform.utils.MinioUtil;
import com.xchange.platform.vo.UploadResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileUploadServiceImpl implements FileUploadService {

    private final MinioUtil minioUtil;
    private final FileUploadAsyncService asyncService; // 注入异步服务

    @Override
    public List<String> uploadAndGetUrls(Long sellerId, Long productId, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        // 调用异步上传
        CompletableFuture<List<UploadResultVO>> future = uploadProductImagesAsync(
                sellerId, productId, "detail", files
        );

        try {
            // 等待结果（30秒超时）
            List<UploadResultVO> results = future.get(30, TimeUnit.SECONDS);

            // 只返回成功的URL
            return results.stream()
                    .filter(UploadResultVO::getSuccess)
                    .map(UploadResultVO::getFileUrl)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("图片上传失败: sellerId={}, error={}", sellerId, e.getMessage());
            throw new RuntimeException("图片上传失败: " + e.getMessage());
        }
    }

    /**
     * 批量上传（异步并行）
     */
    @Override
    public CompletableFuture<List<UploadResultVO>> uploadProductImagesAsync(
            Long sellerId, Long productId, String imageType, List<MultipartFile> files) {

        log.info("开始批量异步上传: sellerId={}, fileCount={}", sellerId, files.size());

        // 1. 创建所有上传任务
        List<CompletableFuture<UploadResultVO>> futures = files.stream()
                .map(file -> asyncService.uploadSingleFileAsync(sellerId, productId, imageType, file))
                .toList();

        // 2. 等待所有任务完成并合并结果
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    // 3. 收集结果（保持原始顺序）
                    List<UploadResultVO> results = futures.stream()
                            .map(CompletableFuture::join) // 获取结果（不会阻塞，因为allOf已确保完成）
                            .collect(Collectors.toList());

                    long successCount = results.stream().filter(UploadResultVO::getSuccess).count();
                    log.info("批量异步上传完成: sellerId={}, success={}, fail={}",
                            sellerId, successCount, files.size() - successCount);

                    return results;
                })
                .exceptionally(ex -> {
                    log.error("批量上传异常: sellerId={}, error={}", sellerId, ex.getMessage());
                    throw new RuntimeException("批量上传失败: " + ex.getMessage());
                });
    }

    /**
     * 兼容单个文件上传
     */
    @Override
    public CompletableFuture<UploadResultVO> uploadSingleFileAsync(
            Long sellerId, Long productId, String imageType, MultipartFile file) {
        return asyncService.uploadSingleFileAsync(sellerId, productId, imageType, file);
    }

    @Override
    public void deleteImage(Long sellerId, String fileUrl) {
        if (StringUtils.isBlank(fileUrl)) {
            throw new RuntimeException("文件URL不能为空");
        }

        try {
            // 从URL中提取bucket和object（简化实现）
            String bucketName = minioUtil.getBucketName();
            String objectName = extractObjectNameFromUrl(fileUrl);

            // 执行删除
            minioUtil.deleteFile(bucketName, objectName);

            log.info("图片删除成功: sellerId={}, fileUrl={}", sellerId, fileUrl);
        } catch (Exception e) {
            log.error("图片删除失败: sellerId={}, error={}", sellerId, e.getMessage());
            throw new RuntimeException("删除失败: " + e.getMessage());
        }
    }

    /**
     * 从URL中提取对象名称（根据实际情况调整）
     */
    private String extractObjectNameFromUrl(String fileUrl) {
        // URL格式：http://localhost:9000/xchange-files/images/cover/2024/01/18/s1_p123_uuid.jpg
        String endpoint = minioUtil.getEndpoint();

        // 移除endpoint部分
        String path = fileUrl.replace(endpoint + "/", "");

        // 移除bucket部分
        String bucketName = minioUtil.getBucketName();
        path = path.replace(bucketName + "/", "");

        return path;
    }
}