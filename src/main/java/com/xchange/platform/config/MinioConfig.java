package com.xchange.platform.config;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient minioClient = MinioClient.builder()
                    .endpoint(minioProperties.getEndpoint())
                    .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                    .build();

            // 测试连接
            minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket("test").build()
            );

            log.info("MinIO客户端初始化成功: endpoint={}", minioProperties.getEndpoint());
            return minioClient;
        } catch (Exception e) {
            log.error("MinIO客户端初始化失败: {}", e.getMessage());
            throw new RuntimeException("MinIO连接失败，请检查配置", e);
        }
    }
}