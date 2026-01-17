package com.xchange.platform.service;

import com.xchange.platform.dto.LoginDTO;
import com.xchange.platform.dto.RegisterDTO;
import com.xchange.platform.dto.UpdatePasswordDTO;
import com.xchange.platform.entity.User;
import com.xchange.platform.vo.UserInfoVO;

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

    /**
     * 获取当前用户信息
     * @param userId 用户ID
     * @return 用户信息视图对象
     */
    UserInfoVO getCurrentUserInfo(Long userId);

    /**
     * 修改用户密码
     * @param userId 用户ID
     * @param updatePasswordDTO 密码修改信息
     */
    void updatePassword(Long userId, UpdatePasswordDTO updatePasswordDTO);

    /**
     * 用户退出登录
     * @param userId 用户ID
     */
    void logout(Long userId);
}