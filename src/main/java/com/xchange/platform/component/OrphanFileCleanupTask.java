package com.xchange.platform.component;

import com.xchange.platform.config.TaskCleanupProperties;
import com.xchange.platform.mapper.ProductImageMapper;
import com.xchange.platform.utils.MinioUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 孤儿文件清理定时任务
 * 清理MinIO中没有被商品引用的图片文件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanFileCleanupTask {

    private final MinioUtil minioUtil;
    private final ProductImageMapper productImageMapper;
    private final TaskCleanupProperties cleanupProperties;

    // 批次大小
    private static final int BATCH_SIZE = 100;

    @Scheduled(cron = "${task.cleanup.orphan-files.cron}")
    public void cleanupOrphanFiles() {
        TaskCleanupProperties.OrphanFiles config = cleanupProperties.getOrphanFiles();

        if (!config.getEnabled()) {
            log.info("========== 孤儿文件清理任务已禁用 ==========");
            return;
        }

        log.info("========== 开始执行孤儿文件清理任务 ==========");
        Instant taskStart = Instant.now();

        try {
            // 1. 获取配置参数
            int expireDays = config.getExpireDays();
            log.info("配置信息: 清理{}天前的文件, Bucket: {}", expireDays, minioUtil.getBucketName());

            // 2. 查询MinIO中的所有图片文件（只扫描images目录）
            List<String> minioObjectNames = minioUtil.listAllObjectNames(
                    minioUtil.getBucketName(),
                    "images/"  // 只清理图片目录
            );

            if (minioObjectNames.isEmpty()) {
                log.info("MinIO中没有需要清理的文件");
                return;
            }

            log.info("MinIO扫描结果: 总文件数={}", minioObjectNames.size());

            // 3. 查询数据库中的所有被引用的图片URL
            Set<String> referencedUrls = productImageMapper.selectAllReferencedUrls();
            log.info("数据库查询结果: 已引用URL数={}", referencedUrls.size());

            // 4. 提取对象名称部分进行比对（URL -> 对象名）
            Set<String> referencedObjectNames = referencedUrls.stream()
                    .map(this::extractObjectNameFromUrl)
                    .filter(StringUtils::isNotBlank)
                    .collect(Collectors.toSet());

            // 5. 找出孤儿文件（在MinIO但不在数据库中）
            List<String> orphanObjectNames = minioObjectNames.stream()
                    .filter(objName -> !referencedObjectNames.contains(objName))
                    .filter(objName -> isExpiredFile(objName, expireDays)) // 过滤过期文件
                    .toList();

            if (orphanObjectNames.isEmpty()) {
                log.info("未找到孤儿文件，跳过清理");
                return;
            }

            log.warn("发现孤儿文件: 数量={}, 示例: {}",
                    orphanObjectNames.size(),
                    orphanObjectNames.stream().limit(3).collect(Collectors.toList()));

            // 6. 分批删除孤儿文件
            int totalDeleted = batchDeleteOrphanFiles(orphanObjectNames);

            // 7. 任务完成统计
            Duration taskDuration = Duration.between(taskStart, Instant.now());
            log.info("========== 孤儿文件清理任务完成 ==========");
            log.info("统计信息: 总耗时={}ms, 扫描文件={}, 孤儿文件={}, 成功删除={}",
                    taskDuration.toMillis(),
                    minioObjectNames.size(),
                    orphanObjectNames.size(),
                    totalDeleted);
        } catch (Exception e) {
            log.error("孤儿文件清理任务执行失败", e);
        }
    }
    /**
     * 从URL中提取对象名称
     * URL示例: http://localhost:9000/xchange-files/images/detail/2024/01/18/s1_p123_uuid.jpg
     * 提取结果: images/detail/2024/01/18/s1_p123_uuid.jpg
     */
    private String extractObjectNameFromUrl(String fileUrl) {
        if (StringUtils.isBlank(fileUrl)) {
            return null;
        }

        try {
            // 移除endpoint部分
            String endpoint = minioUtil.getEndpoint();
            String path = fileUrl.replace(endpoint + "/", "");

            // 移除bucket部分
            String bucketName = minioUtil.getBucketName();
            path = path.replace(bucketName + "/", "");

            return path;
        } catch (Exception e) {
            log.warn("URL解析失败: url={}, error={}", fileUrl, e.getMessage());
            return null;
        }
    }

    /**
     * 判断文件是否已过期（根据文件路径中的日期）
     */
    private boolean isExpiredFile(String objectName, int expireDays) {
        try {
            // 从路径中提取日期：images/detail/2024/01/18/xxx.jpg
            String[] parts = objectName.split("/");
            if (parts.length >= 5) {
                String year = parts[2];
                String month = parts[3];
                String day = parts[4];

                String dateStr = String.format("%s-%s-%s", year, month, day);
                LocalDateTime fileDate = LocalDateTime.parse(dateStr + " 00:00:00",
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

                LocalDateTime expireDate = LocalDateTime.now().minusDays(expireDays);
                return fileDate.isBefore(expireDate);
            }
            return false; // 无法解析日期，默认不删除
        } catch (Exception e) {
            log.warn("日期解析失败: objectName={}, error={}", objectName, e.getMessage());
            return false;
        }
    }

    /**
     * 分批删除孤儿文件
     */
    private int batchDeleteOrphanFiles(List<String> orphanObjectNames) {
        int totalDeleted = 0;
        int batchCount = (int) Math.ceil((double) orphanObjectNames.size() / BATCH_SIZE);

        for (int i = 0; i < batchCount; i++) {
            int fromIndex = i * BATCH_SIZE;
            int toIndex = Math.min(fromIndex + BATCH_SIZE, orphanObjectNames.size());
            List<String> batch = orphanObjectNames.subList(fromIndex, toIndex);

            try {
                log.info("执行删除批次 {}/{}, 数量={}", i + 1, batchCount, batch.size());
                int deleted = minioUtil.batchDeleteObjects(minioUtil.getBucketName(), batch);
                totalDeleted += deleted;

                // 每批之间短暂休眠，避免过载
                if (i < batchCount - 1) {
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                log.error("批次删除失败: batch={}/{}, error={}",
                        i + 1, batchCount, e.getMessage());
            }
        }

        return totalDeleted;
    }
}