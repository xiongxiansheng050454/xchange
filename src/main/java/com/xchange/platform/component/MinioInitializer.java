package com.xchange.platform.component;

import com.xchange.platform.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * MinIO初始化组件
 * 项目启动时自动创建Bucket并设置权限
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MinioInitializer {

    private final MinioUtil minioUtil;
    private final com.xchange.platform.config.MinioProperties minioProperties;

    /**
     * 应用启动完成后执行初始化
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeMinio() {
        try {
            String bucketName = minioProperties.getBucketName();
            log.info("开始初始化MinIO Bucket: {}", bucketName);

            // 确保Bucket存在并设置为公开
            minioUtil.ensureBucketExists(bucketName);

            log.info("MinIO初始化完成！Bucket '{}' 已就绪", bucketName);
        } catch (Exception e) {
            log.error("MinIO初始化失败: {}", e.getMessage());
            // 初始化失败不阻止应用启动，但会影响文件上传功能
        }
    }
}