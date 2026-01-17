package com.xchange.platform.controller;

import com.xchange.platform.common.Result;
import com.xchange.platform.entity.User;
import com.xchange.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
 * 用户相关接口（需要JWT认证）
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserMapper userMapper;

    /**
     * 获取当前登录用户信息
     * GET /api/user/info
     */
    @GetMapping("/info")
    public Result<User> getCurrentUserInfo(HttpServletRequest request) {
        // 从请求属性中获取用户ID（由JwtInterceptor设置）
        Long userId = (Long) request.getAttribute("userId");

        if (userId == null) {
            return Result.error("用户信息获取失败，请重新登录");
        }

        // 查询用户信息
        User user = userMapper.selectById(userId);

        if (user == null) {
            return Result.error("用户不存在");
        }

        return Result.success(user);
    }

    /**
     * 更新用户昵称（演示请求属性使用）
     * PUT /api/user/nickname
     */
    @PutMapping("/nickname")
    public Result<Void> updateNickname(@RequestBody Map<String, String> params, HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        String newNickname = params.get("nickname");

        if (newNickname == null || newNickname.trim().isEmpty()) {
            return Result.error("昵称不能为空");
        }

        User user = new User();
        user.setId(userId);
        user.setNickname(newNickname);

        int updateCount = userMapper.updateById(user);

        if (updateCount > 0) {
            return Result.success("昵称修改成功");
        } else {
            return Result.error("昵称修改失败");
        }
    }
}