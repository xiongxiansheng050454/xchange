package com.xchange.platform.controller;

import com.xchange.platform.common.Result;
import com.xchange.platform.dto.LoginDTO;
import com.xchange.platform.dto.RegisterDTO;
import com.xchange.platform.entity.User;
import com.xchange.platform.service.UserService;
import com.xchange.platform.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterDTO registerDTO) {
        log.info("用户注册请求: username={}", registerDTO.getUsername());

        try {
            // 1. 执行注册
            User user = userService.register(registerDTO);

            // 2. 生成 Token
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", user.getId());
            claims.put("username", user.getUsername());
            claims.put("nickname", user.getNickname());

            String token = jwtUtil.generateToken(claims);

            // 3. 返回数据（脱敏）
            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("userId", user.getId());
            data.put("username", user.getUsername());
            data.put("nickname", user.getNickname());
            data.put("avatar", user.getAvatar());

            return Result.success("注册成功", data);

        } catch (RuntimeException e) {
            log.error("注册失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 用户登录
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO loginDTO) {
        log.info("用户登录请求: username={}", loginDTO.getUsername());

        try {
            Map<String, Object> loginResult = userService.login(loginDTO);
            return Result.success("登录成功", loginResult);

        } catch (RuntimeException e) {
            log.warn("登录失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
}