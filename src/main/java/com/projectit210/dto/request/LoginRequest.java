package com.projectit210.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * DTO đăng nhập
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Tên đăng nhập không được để trống")
    private String username;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}
