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
                        "/swagger-ui/**",
                        "/swagger-ui.html",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml",
                        "/doc.html",
                        "/webjars/**",

                        // ===== 静态资源 =====
                        "/static/**",
                        "/upload/**",
                        "/favicon.ico"
                )
                .order(0); // 设置优先级，数值越小越先执行
    }
}