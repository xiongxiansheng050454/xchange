package com.xchange.platform.interceptor;

import com.xchange.platform.utils.JwtUtil;
import com.xchange.platform.utils.RedisUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.TimeUnit;

/**
 * JWT 认证拦截器
 * 验证请求头中的 Authorization 令牌，并解析用户信息
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Value("${jwt.expiration}")
    private Long expiration; // Token总有效期（毫秒）

    @Value("${jwt.auto-renew-threshold}")
    private Long autoRenewThreshold; // 自动续期阈值（毫秒），默认建议为expiry的20%

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"缺少Token\",\"data\":null}");
            return false;
        }

        String token = authHeader.substring(7);
        Long userId = jwtUtil.getUserIdFromToken(token); // 先解析userId

        // 检查Token是否在黑名单中
        String blackKey = "user:token:blacklist:" + userId;
        if (redisUtil.hasKey(blackKey)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token已失效，请重新登录\",\"data\":null}");
            return false;
        }

        // 验证Token有效性
        if (!jwtUtil.validateToken(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token无效或已过期\",\"data\":null}");
            return false;
        }

        // 验证Redis中Token是否存在
        String redisKey = "user:token:" + userId;
        String storedToken = (String) redisUtil.get(redisKey);
        if (storedToken == null || !storedToken.equals(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token已失效，请重新登录\",\"data\":null}");
            return false;
        }

        // ===== 自动续期核心逻辑 =====
        long remainingTime = redisUtil.getExpireTimeMillis(redisKey);

        // 当剩余时间大于0且小于阈值时，触发续期（避免频繁操作Redis）
        if (remainingTime > 0 && remainingTime < autoRenewThreshold) {
            boolean renewed = redisUtil.expire(redisKey, expiration, TimeUnit.MILLISECONDS);
            if (renewed) {
                log.info("Token自动续期成功: userId={}, 延长{}小时", userId,
                        TimeUnit.MILLISECONDS.toHours(expiration));
            } else {
                log.warn("Token自动续期失败: userId={}", userId);
            }
        }

        // 解析并设置用户信息
        try {
            Claims claims = jwtUtil.parseToken(token);
            request.setAttribute("userId", userId);
            request.setAttribute("username", claims.get("username", String.class));
            request.setAttribute("nickname", claims.get("nickname", String.class));

            log.debug("Token验证成功: userId={}, username={}", userId, request.getAttribute("username"));
            return true;

        } catch (Exception e) {
            log.error("Token解析异常: {}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token解析失败\",\"data\":null}");
            return false;
        }
    }
}