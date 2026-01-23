package com.xchange.platform.component;

import com.xchange.platform.utils.ElasticsearchUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch初始化组件
 * 应用启动时自动创建索引
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchInitializer {

    private final ElasticsearchUtil elasticsearchUtil;

    @EventListener(ApplicationReadyEvent.class)
    public void initializeElasticsearch() {
        try {
            log.info("开始初始化 Elasticsearch 索引...");
            elasticsearchUtil.createProductIndex();
            log.info("Elasticsearch 初始化完成！");
        } catch (Exception e) {
            log.error("Elasticsearch 初始化失败: {}", e.getMessage());
            // 初始化失败不影响应用启动
        }
    }
}