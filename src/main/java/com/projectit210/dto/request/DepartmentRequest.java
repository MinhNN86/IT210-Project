package com.projectit210.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentRequest {

    @NotBlank(message = "Mã Khoa/Ngành không được để trống")
    private String code;

    @NotBlank(message = "Tên Khoa/Ngành không được để trống")
    private String name;

}
