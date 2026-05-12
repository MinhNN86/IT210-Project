package com.projectit210.service;

import com.projectit210.dto.request.LoginRequest;
import com.projectit210.dto.request.RegisterRequest;
import com.projectit210.entity.User;

import java.util.Map;

/**
 * Service xác thực tài khoản (CORE-01)
 */
public interface AuthService {

    /**
     * Đăng ký tài khoản mới. Mật khẩu sẽ được hash bằng BCrypt.
     */
    User register(RegisterRequest request);

    /**
     * Đăng nhập - xác thực username/password.
     * Trả về User nếu thành công.
     */
    User login(LoginRequest request);

    /**
     * Xác thực mã TOTP cho bước 2FA.
     *
     * @param userId ID của user cần xác thực
     * @param code   mã TOTP từ app Google Authenticator
     * @return User nếu mã hợp lệ
     */
    User verifyTotp(String userId, String code);

    /**
     * Tạo secret key TOTP mới cho user và trả về thông tin setup (secret + QR code).
     *
     * @param userId ID của user
     * @return Map chứa "secret" và "qrImageBase64"
     */
    Map<String, String> generateTwoFactorSetup(String userId);

    /**
     * Kích hoạt 2FA cho user sau khi xác nhận mã TOTP đầu tiên.
     *
     * @param userId ID của user
     * @param code   mã TOTP để xác nhận
     * @return true nếu kích hoạt thành công
     */
    boolean enableTwoFactor(String userId, String code);

    /**
     * Vô hiệu hóa 2FA cho user.
     *
     * @param userId ID của user
     * @param code   mã TOTP để xác nhận (bảo mật)
     * @return true nếu vô hiệu hóa thành công
     */
    boolean disableTwoFactor(String userId, String code);
}
