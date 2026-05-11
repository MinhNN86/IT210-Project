package com.projectit210.dto.response;

import lombok.*;

/**
 * DTO hiển thị thông tin thiết bị
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EquipmentResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private Integer quantityInStock;
    private Integer minimumStock;
    private Boolean isActive;
}
