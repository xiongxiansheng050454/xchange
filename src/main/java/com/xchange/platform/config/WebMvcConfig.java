package com.xchange.platform.config;

import com.xchange.platform.interceptor.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**") // 拦截所有请求
                .excludePathPatterns(
                        // ===== 认证相关 =====
                        "/api/auth/register",    // 注册
                        "/api/auth/login",       // 登录

                        // ===== Swagger 文档 =====
                        "/v3/api-docs/**",      // OpenAPI 定义接口
                        "/v3/api-docs.yaml",    // OpenAPI YAML格式
                        "/swagger-ui/**",       // Swagger UI 静态资源
                        "/swagger-ui.html",     // Swagger UI 首页
                        "/doc.html",            // Knife4j增强UI
                        "/webjars/**",          // Swagger 依赖的静态资源

                        // ===== 静态资源 =====
                        "/static/**",
                        "/upload/**",
                        "/favicon.ico"
                )
                .order(0); // 设置优先级，数值越小越先执行
    }
}