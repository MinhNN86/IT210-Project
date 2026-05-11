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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Triển khai AuthService - Đăng ký & Đăng nhập (CORE-01)
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final LecturerRepository lecturerRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

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
}
