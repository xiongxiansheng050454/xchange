package com.xchange.platform;

import com.xchange.platform.config.JwtProperties;
import com.xchange.platform.config.MinioProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({MinioProperties.class, JwtProperties.class})
public class XchangePlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(XchangePlatformApplication.class, args);
    }
}