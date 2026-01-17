package com.xchange.platform.interceptor;

import com.xchange.platform.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器
 * 验证请求头中的 Authorization 令牌，并解析用户信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取 Authorization 头
        String authHeader = request.getHeader("Authorization");

        // 2. 验证 Authorization 头是否存在且格式正确
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("缺少Token: URI={}", request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"缺少Token\",\"data\":null}");
            return false;
        }

        // 3. 提取 Token（移除 "Bearer " 前缀）
        String token = authHeader.substring(7);

        // 4. 验证 Token 有效性
        if (!jwtUtil.validateToken(token)) {
            log.warn("Token无效或已过期: token={}", token);
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\",\"data\":null}");
            return false;
        }

        // 5. 解析 Token，获取用户信息
        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String username = claims.get("username", String.class);
            String nickname = claims.get("nickname", String.class);

            // 6. 将用户信息存入请求属性（供 Controller 使用）
            request.setAttribute("userId", userId);
            request.setAttribute("username", username);
            request.setAttribute("nickname", nickname);

            log.debug("Token验证成功: userId={}, username={}", userId, username);
            return true; // 继续执行请求

        } catch (Exception e) {
            log.error("Token解析异常: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token解析失败\",\"data\":null}");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求完成后清理
        request.removeAttribute("userId");
        request.removeAttribute("username");
        request.removeAttribute("nickname");
    }
}