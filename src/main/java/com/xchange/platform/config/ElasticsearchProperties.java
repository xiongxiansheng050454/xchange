package com.xchange.platform.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {
    private String host = "localhost";
    private Integer port = 9200;
    private String scheme = "http";
    private String username = "elastic";
    private String password = "050454";
    private Integer connectTimeout = 5000;      // 连接超时(ms)
    private Integer socketTimeout = 30000;      // 读取超时(ms)
    private Integer maxConnections = 10;        // 最大连接数
    private Integer maxConnectionsPerRoute = 5; // 单路由最大连接数
}