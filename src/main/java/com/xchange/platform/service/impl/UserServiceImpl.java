package com.xchange.platform.service.impl;

import com.xchange.platform.dto.LoginDTO;
import com.xchange.platform.dto.RegisterDTO;
import com.xchange.platform.dto.UpdatePasswordDTO;
import com.xchange.platform.entity.User;
import com.xchange.platform.mapper.UserMapper;
import com.xchange.platform.service.UserService;
import com.xchange.platform.utils.JwtUtil;
import com.xchange.platform.utils.PasswordUtil;
import com.xchange.platform.utils.RedisUtil;
import com.xchange.platform.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

    @Override
    public void logout(Long userId) {
        // 1. 构建Redis Key
        String redisKey = "user:token:" + userId;

        // 2. 检查Token是否存在
        if (redisUtil.hasKey(redisKey)) {
            // 3. 获取Token的剩余过期时间
            long remainingTime = jwtUtil.getRemainingTime(
                    (String) redisUtil.get(redisKey)
            );

            // 4. 删除Token
            redisUtil.delete(redisKey);
            log.info("用户退出登录，Token已清除: userId={}", userId);

            // 5. 将Token加入短期黑名单（防止在过期前被重用）
            // 这样可以确保即使用户保存了旧Token也无法使用
            String blackKey = "user:token:blacklist:" + userId;
            redisUtil.set(blackKey, "1", remainingTime, TimeUnit.MILLISECONDS);
        } else {
            log.warn("用户退出登录时未找到Redis Token: userId={}", userId);
        }
    }

    @Override
    public void updatePassword(Long userId, UpdatePasswordDTO updatePasswordDTO) {
        // 1. 验证两次新密码是否一致
        if (!updatePasswordDTO.isPasswordMatch()) {
            throw new RuntimeException("两次输入的新密码不一致");
        }

        // 2. 查询用户
        User user = userMapper.selectById(userId);

        if (user == null || Objects.equals(user.getDeleted(), 1)) {
            throw new RuntimeException("用户不存在");
        }

        // 3. 验证旧密码是否正确
        boolean oldPasswordMatch = passwordUtil.matches(
                updatePasswordDTO.getOldPassword(),
                user.getPassword()
        );
        if (!oldPasswordMatch) {
            throw new RuntimeException("旧密码错误");
        }

        // 4. 验证新密码不能与旧密码相同
        if (updatePasswordDTO.getOldPassword().equals(updatePasswordDTO.getNewPassword())) {
            throw new RuntimeException("新密码不能与旧密码相同");
        }

        // 5. 加密新密码
        String encryptedNewPassword = passwordUtil.encrypt(updatePasswordDTO.getNewPassword());

        // 6. 更新数据库
        User updateUser = new User();
        updateUser.setId(userId);
        updateUser.setPassword(encryptedNewPassword);

        int updateCount = userMapper.updateById(updateUser);
        if (updateCount != 1) {
            throw new RuntimeException("密码修改失败，请稍后重试");
        }

        // 7. 删除 Redis 中的 Token（强制重新登录）
        String redisKey = "user:token:" + userId;
        redisUtil.delete(redisKey);

        log.info("用户修改密码成功，已清除Redis Token: userId={}", userId);
    }

    @Override
    public UserInfoVO getCurrentUserInfo(Long userId) {
        // 1. 查询用户
        User user = userMapper.selectById(userId);

        if (user == null || user.getDeleted() == 1) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 转换为 VO（自动排除密码等敏感字段）
        return UserInfoVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .studentId(user.getStudentId())
                .phone(user.getPhone())
                .email(user.getEmail())
                .status(user.getStatus())
                .createTime(user.getCreateTime())
                .build();
    }

    @Override
    public Map<String, Object> login(LoginDTO loginDTO) {
        // 1. 查询用户
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("username", loginDTO.getUsername())
                        .eq("deleted", 0)
        );

        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 2. 验证用户状态
        if (user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用，请联系管理员");
        }

        // 3. 验证密码
        boolean passwordMatch = passwordUtil.matches(loginDTO.getPassword(), user.getPassword());
        if (!passwordMatch) {
            throw new RuntimeException("密码错误");
        }

        // 4. 生成 JWT Token
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("nickname", user.getNickname());

        String token = jwtUtil.generateToken(claims);

        // 5. 存入 Redis（设置与用户配置相同的过期时间）
        long expirationTime = jwtUtil.getRemainingTime(token); // Token剩余时间（毫秒）
        String redisKey = "user:token:" + user.getId();
        redisUtil.set(redisKey, token, expirationTime, TimeUnit.MILLISECONDS);

        // 6. 构建返回数据（脱敏）
        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("avatar", user.getAvatar());
        result.put("studentId", user.getStudentId());
        result.put("phone", user.getPhone());
        result.put("email", user.getEmail());

        return result;
    }

    @Override
    public User register(RegisterDTO registerDTO) {
        // 1. 校验用户名是否已存在
        if (userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("username", registerDTO.getUsername())
                        .eq("deleted", 0)
        ) != null) {
            throw new RuntimeException("用户名已存在");
        }

        // 2. 校验学号是否已存在
        if (userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("student_id", registerDTO.getStudentId())
                        .eq("deleted", 0)
        ) != null) {
            throw new RuntimeException("学号已注册");
        }

        // 3. 创建用户实体
        User user = new User();
        user.setUsername(registerDTO.getUsername());
        user.setNickname(registerDTO.getNickname());
        user.setStudentId(registerDTO.getStudentId());
        user.setPhone(registerDTO.getPhone());
        user.setEmail(registerDTO.getEmail());

        // 4. 加密密码
        String encryptedPassword = passwordUtil.encrypt(registerDTO.getPassword());
        user.setPassword(encryptedPassword);

        // 5. 设置默认头像
        user.setAvatar("https://api.dicebear.com/7.x/avataaars/svg?seed=" + registerDTO.getUsername());

        user.setStatus(1); // 正常状态

        // 6. 插入数据库
        int insertCount = userMapper.insert(user);
        if (insertCount != 1) {
            throw new RuntimeException("注册失败，请稍后重试");
        }

        return user;
    }

    @Override
    public User getByUsername(String username) {
        return userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("username", username)
                        .eq("deleted", 0)
        );
    }

    @Override
    public User getByStudentId(String studentId) {
        return userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("student_id", studentId)
                        .eq("deleted", 0)
        );
    }
}