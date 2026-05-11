package com.projectit210.mapper;

import com.projectit210.dto.request.EquipmentRequest;
import com.projectit210.dto.response.EquipmentResponse;
import com.projectit210.entity.Equipment;
import org.springframework.stereotype.Component;

/**
 * Mapper chuyển đổi giữa Equipment entity và DTOs
 */
@Component
public class EquipmentMapper {

    public EquipmentResponse toResponse(Equipment equipment) {
        if (equipment == null) return null;
        return EquipmentResponse.builder()
                .id(equipment.getId())
                .code(equipment.getCode())
                .name(equipment.getName())
                .description(equipment.getDescription())
                .quantityInStock(equipment.getQuantityInStock())
                .minimumStock(equipment.getMinimumStock())
                .isActive(equipment.getIsActive())
                .build();
    }

    public Equipment toEntity(EquipmentRequest request) {
        if (request == null) return null;
        return Equipment.builder()
                .code(request.getCode())
                .name(request.getName())
                .description(request.getDescription())
                .quantityInStock(request.getQuantityInStock())
                .minimumStock(request.getMinimumStock() != null ? request.getMinimumStock() : 0)
                .build();
    }

    public void updateEntity(Equipment equipment, EquipmentRequest request) {
        equipment.setCode(request.getCode());
        equipment.setName(request.getName());
        equipment.setDescription(request.getDescription());
        equipment.setQuantityInStock(request.getQuantityInStock());
        if (request.getMinimumStock() != null) {
            equipment.setMinimumStock(request.getMinimumStock());
        }
    }
}
