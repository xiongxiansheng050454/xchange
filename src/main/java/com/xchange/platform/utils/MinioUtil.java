package com.xchange.platform.utils;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.DeleteError;
import io.minio.messages.DeleteObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.xchange.platform.config.MinioProperties;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import io.minio.messages.Item;
/**
 * MinIO 工具类
 * 封装文件上传、下载、删除等操作
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioUtil {

    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    /**
     * 列出Bucket中所有对象名称（支持分页）
     * @param bucketName Bucket名称
     * @param prefix 前缀过滤（如：images/）
     * @return 对象名称列表
     */
    public List<String> listAllObjectNames(String bucketName, String prefix) {
        List<String> objectNames = new ArrayList<>();

        try {
            log.info("开始扫描MinIO文件: bucket={}, prefix={}", bucketName, prefix);

            // 分页获取所有对象（每次1000条）
            Iterable<Result<Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucketName)
                            .prefix(prefix)
                            .recursive(true)  // 递归子目录
                            .build()
            );

            int count = 0;
            for (Result<Item> result : results) {
                Item item = result.get();
                if (!item.isDir()) { // 只收集文件，跳过目录
                    objectNames.add(item.objectName());
                    count++;
                }
            }

            log.info("MinIO文件扫描完成: bucket={}, totalFiles={}", bucketName, count);
            return objectNames;
        } catch (Exception e) {
            log.error("扫描MinIO文件失败: bucket={}, error={}", bucketName, e.getMessage());
            throw new RuntimeException("扫描MinIO文件失败", e);
        }
    }

    /**
     * 判断Bucket是否存在，不存在则创建
     */
    public void ensureBucketExists(String bucketName) {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(bucketName).build()
            );

            if (!exists) {
                log.info("Bucket不存在，正在创建: {}", bucketName);
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(bucketName).build()
                );

                // 设置Bucket公开访问权限（用于图片访问）
                setBucketPolicyPublic(bucketName);

                log.info("Bucket创建成功并设置为公开: {}", bucketName);
            } else {
                log.debug("Bucket已存在: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Bucket操作失败: {}", e.getMessage());
            throw new RuntimeException("创建Bucket失败", e);
        }
    }

    /**
     * 设置Bucket为公开访问（允许任何人读取文件）
     */
    private void setBucketPolicyPublic(String bucketName) throws Exception {
        String policy = """
            {
                "Version": "2012-10-17",
                "Statement": [{
                    "Effect": "Allow",
                    "Principal": "*",
                    "Action": ["s3:GetObject"],
                    "Resource": ["arn:aws:s3:::%s/*"]
                }]
            }
            """.formatted(bucketName);

        minioClient.setBucketPolicy(
                SetBucketPolicyArgs.builder()
                        .bucket(bucketName)
                        .config(policy)
                        .build()
        );
    }

    /**
     * 上传文件（自动创建Bucket）
     * @param file 上传的文件
     * @param objectName 对象名称（如：products/2024/01/18/12345.jpg）
     * @return 文件访问URL
     */
    public String uploadFile(MultipartFile file, String objectName) {
        return uploadFile(file, minioProperties.getBucketName(), objectName);
    }

    /**
     * 上传文件到指定Bucket
     */
    public String uploadFile(MultipartFile file, String bucketName, String objectName) {
        try {
            // 确保Bucket存在
            ensureBucketExists(bucketName);

            // 上传文件
            InputStream inputStream = file.getInputStream();
            long size = file.getSize();
            String contentType = file.getContentType();

            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType)
                    .build();

            minioClient.putObject(args);

            log.info("文件上传成功: bucket={}, object={}", bucketName, objectName);

            // 返回可访问的URL
            return generatePublicUrl(bucketName, objectName);
        } catch (Exception e) {
            log.error("文件上传失败: {}", e.getMessage());
            throw new RuntimeException("文件上传失败", e);
        }
    }

    /**
     * 生成公开访问URL
     */
    public String generatePublicUrl(String bucketName, String objectName) {
        String endpoint = minioProperties.getEndpoint();

        // 移除协议头，统一处理
        String cleanEndpoint = endpoint.replaceAll("^https?://", "");

        // 如果endpoint包含端口
        if (cleanEndpoint.contains(":")) {
            return String.format("%s/%s/%s", endpoint, bucketName, objectName);
        } else {
            // 默认端口80/443
            return String.format("%s/%s/%s", endpoint, bucketName, objectName);
        }
    }

    /**
     * 删除文件
     */
    public void deleteFile(String bucketName, String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("文件删除成功: bucket={}, object={}", bucketName, objectName);
        } catch (Exception e) {
            log.error("文件删除失败: {}", e.getMessage());
            throw new RuntimeException("文件删除失败", e);
        }
    }

    /**
     * 生成预签名URL（临时访问链接，用于私有文件）
     * @param bucketName Bucket名称
     * @param objectName 对象名称
     * @param expiry 过期时间（分钟）
     * @return 预签名URL
     */
    public String generatePresignedUrl(String bucketName, String objectName, int expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(expiry, TimeUnit.MINUTES)
                            .build()
            );
        } catch (Exception e) {
            log.error("生成预签名URL失败: {}", e.getMessage());
            throw new RuntimeException("生成临时链接失败", e);
        }
    }

    /**
     * 批量删除对象（性能优化）
     * @param bucketName Bucket名称
     * @param objectNames 要删除的对象名称列表
     * @return 成功删除的数量
     */
    public int batchDeleteObjects(String bucketName, List<String> objectNames) {
        if (objectNames == null || objectNames.isEmpty()) {
            return 0;
        }

        try {
            log.info("开始批量删除MinIO文件: bucket={}, count={}", bucketName, objectNames.size());

            // MinIO支持批量删除（每次最多1000个）
            List<DeleteObject> deleteObjects = objectNames.stream()
                    .map(DeleteObject::new)
                    .collect(Collectors.toList());

            Iterable<Result<DeleteError>> results = minioClient.removeObjects(
                    RemoveObjectsArgs.builder()
                            .bucket(bucketName)
                            .objects(deleteObjects)
                            .build()
            );

            // 遍历结果检查错误
            int successCount = 0;
            int failCount = 0;
            for (Result<DeleteError> result : results) {
                try {
                    result.get(); // 检查是否有错误
                    successCount++;
                } catch (Exception e) {
                    failCount++;
                    log.warn("删除单个文件失败: {}", e.getMessage());
                }
            }

            log.info("批量删除完成: bucket={}, success={}, fail={}",
                    bucketName, successCount, failCount);
            return successCount;
        } catch (Exception e) {
            log.error("批量删除失败: bucket={}, error={}", bucketName, e.getMessage());
            throw new RuntimeException("批量删除失败", e);
        }
    }

    /**
     * 获取 MinIO 服务端点地址
     */
    public String getEndpoint() {
        return minioProperties.getEndpoint();
    }

    /**
     * 获取默认 Bucket 名称
     */
    public String getBucketName() {
        return minioProperties.getBucketName();
    }

}