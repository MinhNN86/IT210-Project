package com.projectit210.dto.response;

import lombok.*;

/**
 * DTO phản hồi xác thực - chứa JWT token
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String token;
    private String userId;
    private String username;
    private String role;
    private String fullName;
}
