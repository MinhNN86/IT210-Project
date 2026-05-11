package com.projectit210.service.impl;

import com.projectit210.entity.Equipment;
import com.projectit210.exception.ResourceNotFoundException;
import com.projectit210.repository.EquipmentRepository;
import com.projectit210.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CORE-08: Quản lý tồn kho thiết bị
 */
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final EquipmentRepository equipmentRepository;

    @Override
    public boolean checkStock(Long equipmentId, int quantity) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Thiết bị không tồn tại"));
        return equipment.getQuantityInStock() >= quantity;
    }

    @Override
    @Transactional
    public void reduceStock(Long equipmentId, int quantity) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Thiết bị không tồn tại"));
        equipment.setQuantityInStock(equipment.getQuantityInStock() - quantity);
        equipmentRepository.save(equipment);
    }

    @Override
    @Transactional
    public void restoreStock(Long equipmentId, int quantity) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Thiết bị không tồn tại"));
        equipment.setQuantityInStock(equipment.getQuantityInStock() + quantity);
        equipmentRepository.save(equipment);
    }
}
