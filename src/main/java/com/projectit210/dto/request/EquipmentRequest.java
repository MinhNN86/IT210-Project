package com.projectit210.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * DTO thêm/sửa thiết bị (CORE-04)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentRequest {

    @NotBlank(message = "Mã thiết bị không được để trống")
    private String code;

    @NotBlank(message = "Tên thiết bị không được để trống")
    private String name;

    private String description;

    @NotNull(message = "Số lượng tồn kho không được để trống")
    @Min(value = 0, message = "Số lượng tồn kho phải >= 0")
    private Integer quantityInStock;

    @Min(value = 0, message = "Tồn kho tối thiểu phải >= 0")
    private Integer minimumStock;
}
