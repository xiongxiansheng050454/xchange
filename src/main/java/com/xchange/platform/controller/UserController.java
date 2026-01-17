package com.xchange.platform.controller;

import com.xchange.platform.common.Result;
import com.xchange.platform.dto.UpdatePasswordDTO;
import com.xchange.platform.service.UserService;
import com.xchange.platform.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 用户相关接口（需要JWT认证）
 */
@Slf4j
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户信息相关接口")
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息
     * GET /api/user/info
     */
    @GetMapping("/info")
    @Operation(summary = "获取当前用户信息", description = "需要携带有效的JWT Token")
    public Result<UserInfoVO> getCurrentUserInfo(
            @RequestAttribute("userId") Long userId) {

        try {
            UserInfoVO userInfo = userService.getCurrentUserInfo(userId);
            return Result.success(userInfo);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 修改用户密码
     * PUT /api/user/password
     */
    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "验证旧密码后更新为新密码")
    public Result<Void> updatePassword(
            @RequestAttribute("userId") Long userId,
            @Valid @RequestBody UpdatePasswordDTO updatePasswordDTO) {

        log.info("用户修改密码请求: userId={}", userId);

        try {
            userService.updatePassword(userId, updatePasswordDTO);
            return Result.success("密码修改成功，请重新登录");
        } catch (RuntimeException e) {
            log.warn("密码修改失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

}