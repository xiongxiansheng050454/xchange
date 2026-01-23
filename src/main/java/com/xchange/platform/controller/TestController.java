package com.xchange.platform.controller;

import com.xchange.platform.common.Result;
import com.xchange.platform.component.OrphanFileCleanupTask;
import com.xchange.platform.utils.ElasticsearchUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class TestController {
    private final OrphanFileCleanupTask cleanupTask;

    @PostMapping("/cleanup")
    public Result<String> triggerCleanup() {
        cleanupTask.cleanupOrphanFiles();
        return Result.success("清理任务已手动触发");
    }

    private final ElasticsearchUtil esUtil;

    @GetMapping("/es-health")
    public Result<String> checkEsHealth() {
        try {
            boolean exists = esUtil.indexExists("xchange_products");
            return Result.success("Elasticsearch连接正常，索引存在: " + exists);
        } catch (Exception e) {
            return Result.error("Elasticsearch连接失败: " + e.getMessage());
        }
    }

    @PostMapping("/es-index")
    public Result<Void> createIndex() {
        esUtil.createProductIndex();
        return Result.success("索引创建成功");
    }
}