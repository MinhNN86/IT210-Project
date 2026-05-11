package com.projectit210.service.impl;

import com.projectit210.dto.request.EquipmentRequest;
import com.projectit210.dto.response.EquipmentResponse;
import com.projectit210.entity.Equipment;
import com.projectit210.exception.BadRequestException;
import com.projectit210.exception.ResourceNotFoundException;
import com.projectit210.mapper.EquipmentMapper;
import com.projectit210.repository.EquipmentRepository;
import com.projectit210.service.EquipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Triển khai EquipmentService - CRUD thiết bị (CORE-04)
 */
@Service
@RequiredArgsConstructor
public class EquipmentServiceImpl implements EquipmentService {

    private final EquipmentRepository equipmentRepository;
    private final EquipmentMapper equipmentMapper;

    @Override
    public List<EquipmentResponse> findAll() {
        return equipmentRepository.findAll().stream()
                .map(equipmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<EquipmentResponse> findAllActive() {
        return equipmentRepository.findByIsActiveTrue().stream()
                .map(equipmentMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public EquipmentResponse findById(Long id) {
        Equipment equipment = findEntityById(id);
        return equipmentMapper.toResponse(equipment);
    }

    @Override
    public Equipment findEntityById(Long id) {
        return equipmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Thiết bị không tồn tại với ID: " + id));
    }

    @Override
    @Transactional
    public EquipmentResponse create(EquipmentRequest request) {
        if (equipmentRepository.existsByCode(request.getCode())) {
            throw new BadRequestException("Mã thiết bị đã tồn tại: " + request.getCode());
        }
        Equipment equipment = equipmentMapper.toEntity(request);
        equipment = equipmentRepository.save(equipment);
        return equipmentMapper.toResponse(equipment);
    }

    @Override
    @Transactional
    public EquipmentResponse update(Long id, EquipmentRequest request) {
        Equipment equipment = findEntityById(id);

        // Kiểm tra mã trùng (trừ chính nó)
        equipmentRepository.findByCode(request.getCode()).ifPresent(existing -> {
            if (!existing.getId().equals(id)) {
                throw new BadRequestException("Mã thiết bị đã tồn tại: " + request.getCode());
            }
        });

        equipmentMapper.updateEntity(equipment, request);
        equipment = equipmentRepository.save(equipment);
        return equipmentMapper.toResponse(equipment);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Equipment equipment = findEntityById(id);
        equipment.setIsActive(false); // Soft delete
        equipmentRepository.save(equipment);
    }
}
