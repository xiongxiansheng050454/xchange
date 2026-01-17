package com.xchange.platform.service;

import com.xchange.platform.dto.LoginDTO;
import com.xchange.platform.dto.RegisterDTO;
import com.xchange.platform.entity.User;

import java.util.Map;


public interface UserService {
    /**
     * 用户注册
     * @param registerDTO 注册信息
     * @return 注册用户实体
     */
    User register(RegisterDTO registerDTO);

    /**
     * 用户登录
     * @param loginDTO 登录信息
     * @return 包含用户信息和Token的Map
     */
    Map<String, Object> login(LoginDTO loginDTO);

    /**
     * 根据用户名查询用户
     */
    User getByUsername(String username);

    /**
     * 根据学号查询用户
     */
    User getByStudentId(String studentId);
}