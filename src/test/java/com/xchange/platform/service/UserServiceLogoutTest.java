package com.xchange.platform.service;

import com.xchange.platform.service.impl.UserServiceImpl;
import com.xchange.platform.utils.JwtUtil;
import com.xchange.platform.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceLogoutTest {

    @Mock
    private RedisUtil redisUtil;

    @Mock
    private JwtUtil jwtUtil;  // 添加 JwtUtil 的 Mock

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void testLogout_Success() {
        Long userId = 1L;
        String redisKey = "user:token:" + userId;
        String token = "test-token";
        long remainingTime = 3600000L; // 1小时，单位毫秒

        // Mock Redis 操作
        when(redisUtil.hasKey(redisKey)).thenReturn(true);
        when(redisUtil.get(redisKey)).thenReturn(token);

        // Mock JwtUtil
        when(jwtUtil.getRemainingTime(token)).thenReturn(remainingTime);

        // 执行登出
        userService.logout(userId);

        // 验证 Redis Token 被删除，并且调用了jwtUtil
        verify(jwtUtil).getRemainingTime(token);
        verify(redisUtil).delete(redisKey);
    }

    @Test
    void testLogout_TokenNotExist() {
        Long userId = 1L;
        String redisKey = "user:token:" + userId;

        // Mock Redis 中无Token
        when(redisUtil.hasKey(redisKey)).thenReturn(false);

        // 执行登出（不应抛出异常）
        assertDoesNotThrow(() -> userService.logout(userId));

        // 验证 delete 和 jwtUtil 均未被调用
        verify(redisUtil, never()).delete(anyString());
        verify(jwtUtil, never()).getRemainingTime(anyString());
    }
}