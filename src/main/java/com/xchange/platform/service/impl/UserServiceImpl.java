package com.xchange.platform.service.impl;

import com.xchange.platform.dto.LoginDTO;
import com.xchange.platform.dto.RegisterDTO;
import com.xchange.platform.entity.User;
import com.xchange.platform.mapper.UserMapper;
import com.xchange.platform.service.UserService;
import com.xchange.platform.utils.JwtUtil;
import com.xchange.platform.utils.PasswordUtil;
import com.xchange.platform.utils.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordUtil passwordUtil;
    private final JwtUtil jwtUtil;
    private final RedisUtil redisUtil;

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