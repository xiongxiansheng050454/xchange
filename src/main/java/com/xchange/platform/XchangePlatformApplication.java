package com.xchange.platform;

import com.xchange.platform.config.JwtProperties;
import com.xchange.platform.config.MinioProperties;
import com.xchange.platform.config.TaskCleanupProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
@EnableConfigurationProperties({
        MinioProperties.class,
        JwtProperties.class,
        TaskCleanupProperties.class,
})
public class XchangePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(XchangePlatformApplication.class, args);
    }
}