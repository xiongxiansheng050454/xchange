package com.xchange.platform.controller;

import com.xchange.platform.utils.JwtUtil;
import com.xchange.platform.vo.UserInfoVO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private com.xchange.platform.service.UserService userService;

    @Autowired
    private JwtUtil jwtUtil; // 注入真实的 JwtUtil 用于生成 Token

    @Test
    void testGetCurrentUserInfo_Success() throws Exception {
        // 1. 生成一个有效的 JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", 1L);
        claims.put("username", "zhangsan");
        claims.put("nickname", "张三");
        String token = jwtUtil.generateToken(claims); // 使用真实 JwtUtil 生成

        // 2. Mock UserService 返回
        UserInfoVO userInfo = UserInfoVO.builder()
                .id(1L)
                .username("zhangsan")
                .nickname("张三")
                .studentId("202100123456")
                .phone("13812345678")
                .email("zhangsan@qq.com")
                .status(1)
                .build();

        when(userService.getCurrentUserInfo(anyLong())).thenReturn(userInfo);

        // 3. 发起请求，携带 Authorization 头
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/info")
                        .header("Authorization", "Bearer " + token) // 模拟真实请求
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk()) // 期望 200
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(200))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.username").value("zhangsan"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.password").doesNotExist()); // 验证无密码
    }

    @Test
    void testGetCurrentUserInfo_Unauthorized() throws Exception {
        // 不设置 Authorization 头，模拟无 Token
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/info")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized()) // 期望 401
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(401))
                .andExpect(MockMvcResultMatchers.jsonPath("$.message").value("缺少Token"));
    }

    @Test
    void testGetCurrentUserInfo_InvalidToken() throws Exception {
        // 使用无效的 Token
        mockMvc.perform(MockMvcRequestBuilders.get("/api/user/info")
                        .header("Authorization", "Bearer invalid_token_here")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized()) // 期望 401
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value(401));
    }
}