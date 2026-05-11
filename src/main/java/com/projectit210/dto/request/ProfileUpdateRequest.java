package com.projectit210.dto.request;

import lombok.*;

import java.time.LocalDate;

/**
 * DTO cập nhật hồ sơ cá nhân (CORE-03)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileUpdateRequest {

    private String username;
    private String email;
    private String fullName;
    private String phone;
    private String gender; // MALE, FEMALE, OTHER
    private LocalDate dob;
    private String address;
}
