package com.projectit210.service;

import com.projectit210.dto.request.LoginRequest;
import com.projectit210.dto.request.RegisterRequest;
import com.projectit210.entity.User;

/**
 * Service xác thực tài khoản (CORE-01)
 */
public interface AuthService {

    /**
     * Đăng ký tài khoản mới. Mật khẩu sẽ được hash bằng BCrypt.
     */
    User register(RegisterRequest request);

    /**
     * Đăng nhập - xác thực username/password
     */
    User login(LoginRequest request);
}
