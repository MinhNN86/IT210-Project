package com.projectit210.service.impl;

import com.projectit210.dto.request.LoginRequest;
import com.projectit210.dto.request.RegisterRequest;
import com.projectit210.entity.Department;
import com.projectit210.entity.Lecturer;
import com.projectit210.entity.User;
import com.projectit210.enums.Role;
import com.projectit210.exception.BadRequestException;
import com.projectit210.exception.ResourceNotFoundException;
import com.projectit210.repository.DepartmentRepository;
import com.projectit210.repository.LecturerRepository;
import com.projectit210.repository.UserRepository;
import com.projectit210.service.AuthService;
import com.projectit210.util.TotpUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Triển khai AuthService - Đăng ký, Đăng nhập & 2FA (CORE-01)
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final LecturerRepository lecturerRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final TotpUtil totpUtil;

    @Override
    @Transactional
    public User register(RegisterRequest request) {
        // Validate mật khẩu xác nhận
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu xác nhận không khớp");
        }

        // Kiểm tra username/email đã tồn tại
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Tên đăng nhập đã tồn tại");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email đã được sử dụng");
        }

        // Xác định role
        Role role;
        try {
            role = Role.valueOf(request.getRole().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Vai trò không hợp lệ");
        }

        // Chỉ cho phép đăng ký tài khoản Sinh viên. Giảng viên do Admin tạo.
        if (role == Role.ADMIN) {
            throw new BadRequestException("Không thể đăng ký tài khoản Admin");
        }
        if (role == Role.LECTURER) {
            throw new BadRequestException("Không thể đăng ký tài khoản Giảng viên. Vui lòng liên hệ Quản trị viên.");
        }

        // Tạo user với mật khẩu đã hash (CORE-01: bắt buộc hash password)
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .fullName(request.getFullName())
                .isActive(true)
                .twoFactorEnabled(false)
                .build();

        user = userRepository.save(user);

        // Nếu là giảng viên, tạo thêm Lecturer record
        if (role == Role.LECTURER) {
            if (request.getDepartmentId() == null) {
                throw new BadRequestException("Giảng viên phải chọn Khoa/Ngành");
            }
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Khoa/Ngành không tồn tại"));

            Lecturer lecturer = Lecturer.builder()
                    .user(user)
                    .department(department)
                    .academicRank(request.getAcademicRank())
                    .specialization(request.getSpecialization())
                    .build();
            lecturerRepository.save(lecturer);
        }

        return user;
    }

    @Override
    public User login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadRequestException("Tên đăng nhập hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Tên đăng nhập hoặc mật khẩu không đúng");
        }

        if (!user.getIsActive()) {
            throw new BadRequestException("Tài khoản đã bị vô hiệu hóa");
        }

        return user;
    }

    @Override
    public User verifyTotp(String userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BadRequestException("Người dùng không tồn tại"));

        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new BadRequestException("2FA chưa được kích hoạt cho tài khoản này");
        }

        if (user.getTwoFactorSecret() == null) {
            throw new BadRequestException("Không tìm thấy khóa bảo mật 2FA");
        }

        try {
            int totpCode = Integer.parseInt(code);
            if (!totpUtil.verifyCode(user.getTwoFactorSecret(), totpCode)) {
                throw new BadRequestException("Mã xác thực không đúng. Vui lòng thử lại.");
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("Mã xác thực phải là số");
        }

        return user;
    }

    @Override
    @Transactional
    public Map<String, String> generateTwoFactorSetup(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new BadRequestException("2FA đã được kích hoạt. Vui lòng vô hiệu hóa trước khi thiết lập lại.");
        }

        // Generate new secret
        String secret = totpUtil.generateSecret();

        // Temporarily store the secret (not enabled yet)
        user.setTwoFactorSecret(secret);
        userRepository.save(user);

        // Generate QR code (300x300 for better scanning)
        String otpAuthUrl = totpUtil.getOtpAuthUrl(user.getUsername(), secret);
        String qrImageBase64 = totpUtil.generateQrCodeBase64(otpAuthUrl, 300, 300);

        Map<String, String> result = new HashMap<>();
        result.put("secret", secret);
        result.put("qrImageBase64", qrImageBase64);
        return result;
    }

    @Override
    @Transactional
    public boolean enableTwoFactor(String userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (user.getTwoFactorSecret() == null) {
            throw new BadRequestException("Vui lòng tạo khóa bảo mật trước khi kích hoạt 2FA");
        }

        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new BadRequestException("2FA đã được kích hoạt");
        }

        try {
            int totpCode = Integer.parseInt(code);
            if (!totpUtil.verifyCode(user.getTwoFactorSecret(), totpCode)) {
                throw new BadRequestException("Mã xác thực không đúng. Vui lòng thử lại.");
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("Mã xác thực phải là số");
        }

        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        return true;
    }

    @Override
    @Transactional
    public boolean disableTwoFactor(String userId, String code) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new BadRequestException("2FA chưa được kích hoạt");
        }

        try {
            int totpCode = Integer.parseInt(code);
            if (!totpUtil.verifyCode(user.getTwoFactorSecret(), totpCode)) {
                throw new BadRequestException("Mã xác thực không đúng. Vui lòng thử lại.");
            }
        } catch (NumberFormatException e) {
            throw new BadRequestException("Mã xác thực phải là số");
        }

        user.setTwoFactorEnabled(false);
        user.setTwoFactorSecret(null);
        userRepository.save(user);
        return true;
    }
}
