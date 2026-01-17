package com.xchange.platform.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class JwtUtilTest {

    @Autowired
    private JwtUtil jwtUtil;

    @Test
    void testGenerateAndValidateToken() {
        // 准备数据
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1001L);
        claims.put("username", "zhangsan");
        claims.put("nickname", "张三");

        // 生成 Token
        String token = jwtUtil.generateToken(claims);
        System.out.println("生成的Token: " + token);
        assertNotNull(token);

        // 验证 Token
        boolean isValid = jwtUtil.validateToken(token);
        assertTrue(isValid);

        // 解析 Token
        Long userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        assertEquals(1001L, userId);
        assertEquals("zhangsan", username);
    }

    @Test
    void testExpiredToken() throws InterruptedException {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1001L);

        String token = jwtUtil.generateToken(claims);

        // 等待过期（假设配置文件中 expiration=5000，即5秒）
        TimeUnit.SECONDS.sleep(6);

        boolean isValid = jwtUtil.validateToken(token);
        assertFalse(isValid);
    }

    @Test
    void testRefreshToken() throws InterruptedException {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1001L);

        String oldToken = jwtUtil.generateToken(claims);
        System.out.println("旧Token: " + oldToken);

        TimeUnit.SECONDS.sleep(2); // 等待2秒

        String newToken = jwtUtil.refreshToken(oldToken);
        System.out.println("新Token: " + newToken);

        assertNotEquals(oldToken, newToken);
        assertTrue(jwtUtil.validateToken(newToken));
    }

    @Test
    void testTokenExpiringSoon() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1001L);

        String token = jwtUtil.generateToken(claims);

        // 立即判断，应该还有很长时间才过期
        boolean expiringSoon = jwtUtil.isTokenExpiringSoon(token);
        assertFalse(expiringSoon);
    }
}