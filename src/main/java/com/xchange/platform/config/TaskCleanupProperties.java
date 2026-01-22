package com.xchange.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 任务清理配置属性
 * 映射 application.yml 中的 task.cleanup 配置
 */
@Data
@ConfigurationProperties(prefix = "task.cleanup")
public class TaskCleanupProperties {

    private OrphanFiles orphanFiles = new OrphanFiles();

    @Data
    public static class OrphanFiles {
        private Boolean enabled = false;
        private String cron = "0 0 2 * * ?";
        private Integer expireDays = 1;
    }
}