package com.xchange.platform.interceptor;

import com.xchange.platform.utils.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtInterceptorTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private JwtInterceptor jwtInterceptor;

    @Test
    void testPreHandle_Success() throws Exception {
        // 模拟 Token
        String token = "eyJhbGciOiJIUzI1NiJ9.eyJuaWNrbmFtZSI6IuW8oOS4iSIsInVzZXJJZCI6MSwidXNlcm5hbWUiOiJ6aGFuZ3NhbiIsImlhdCI6MTc2ODYxMjU0MywiZXhwIjoxNzY4Njk4OTQzfQ.y2VQzK7YhBA7yUiiEH93fXs7WWy5aEdNkX_Njz_tvq8";
        String bearerToken = "Bearer " + token;

        // Mock Request
        when(request.getHeader("Authorization")).thenReturn(bearerToken);
        when(jwtUtil.validateToken(token)).thenReturn(true);

        // Mock Token 解析
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1L);
        claims.put("username", "zhangsan");
        when(jwtUtil.parseToken(token)).thenReturn(new io.jsonwebtoken.impl.DefaultClaims(claims));

        // 执行拦截
        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        // 验证
        assertTrue(result);
        verify(request).setAttribute("userId", 1L);
        verify(request).setAttribute("username", "zhangsan");
    }

    @Test
    void testPreHandle_MissingToken() throws Exception {
        // Mock 无 Authorization 头
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/user/info");

        // Mock Response
        when(response.getWriter()).thenReturn(mock(java.io.PrintWriter.class));

        // 执行拦截
        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        // 验证被拦截
        assertFalse(result);
        verify(response).setStatus(401);
    }

    @Test
    void testPreHandle_InvalidToken() throws Exception {
        // Mock 无效 Token
        String token = "invalid-token";
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtUtil.validateToken(token)).thenReturn(false);
        when(response.getWriter()).thenReturn(mock(java.io.PrintWriter.class));

        // 执行拦截
        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        // 验证被拦截
        assertFalse(result);
        verify(response).setStatus(401);
    }
}