package com.xchange.platform.service;

import com.xchange.platform.dto.UpdatePasswordDTO;
import com.xchange.platform.entity.User;
import com.xchange.platform.mapper.UserMapper;
import com.xchange.platform.service.impl.UserServiceImpl;
import com.xchange.platform.utils.PasswordUtil;
import com.xchange.platform.utils.RedisUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceUpdatePasswordTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordUtil passwordUtil;

    @Mock
    private RedisUtil redisUtil;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void testUpdatePassword_Success() {
        // 准备数据
        Long userId = 1L;
        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("oldPass123");
        dto.setNewPassword("newPass456");
        dto.setConfirmPassword("newPass456");

        User user = new User();
        user.setId(userId);
        user.setPassword("encrypted_old_password");
        user.setDeleted(0); // ✅ 修复：必须设置 deleted 字段

        // Mock
        when(userMapper.selectById(userId)).thenReturn(user);
        when(passwordUtil.matches(dto.getOldPassword(), user.getPassword())).thenReturn(true);
        when(passwordUtil.encrypt(dto.getNewPassword())).thenReturn("encrypted_new_password");
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        // 执行
        assertDoesNotThrow(() -> userService.updatePassword(userId, dto));

        // 验证
        verify(redisUtil).delete("user:token:" + userId);
    }

    @Test
    void testUpdatePassword_WrongOldPassword() {
        Long userId = 1L;
        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("wrongOldPass");
        dto.setNewPassword("newPass456");
        dto.setConfirmPassword("newPass456");

        User user = new User();
        user.setId(userId);
        user.setPassword("encrypted_old_password");
        user.setDeleted(0); // ✅ 修复：必须设置 deleted 字段

        when(userMapper.selectById(userId)).thenReturn(user);
        when(passwordUtil.matches(dto.getOldPassword(), user.getPassword())).thenReturn(false);

        // 执行并验证异常
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.updatePassword(userId, dto));
        assertEquals("旧密码错误", exception.getMessage());
    }

    @Test
    void testUpdatePassword_UserNotFound() {
        Long userId = 999L;
        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("oldPass123");
        dto.setNewPassword("newPass456");
        dto.setConfirmPassword("newPass456");

        // Mock 返回 null（用户不存在）
        when(userMapper.selectById(userId)).thenReturn(null);

        // 执行并验证异常
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.updatePassword(userId, dto));
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void testUpdatePassword_UserDeleted() {
        Long userId = 1L;
        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("oldPass123");
        dto.setNewPassword("newPass456");
        dto.setConfirmPassword("newPass456");

        User user = new User();
        user.setId(userId);
        user.setPassword("encrypted_old_password");
        user.setDeleted(1); // ✅ 已删除用户

        when(userMapper.selectById(userId)).thenReturn(user);

        // 执行并验证异常
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.updatePassword(userId, dto));
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void testUpdatePassword_NewPasswordNotMatch() {
        UpdatePasswordDTO dto = new UpdatePasswordDTO();
        dto.setOldPassword("oldPass123");
        dto.setNewPassword("newPass456");
        dto.setConfirmPassword("differentPass");

        // 执行并验证异常
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.updatePassword(1L, dto));
        assertEquals("两次输入的新密码不一致", exception.getMessage());
    }
}