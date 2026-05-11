package com.projectit210.service;

import com.projectit210.dto.request.EquipmentRequest;
import com.projectit210.dto.response.EquipmentResponse;
import com.projectit210.entity.Equipment;

import java.util.List;

/**
 * Service quản lý thiết bị (CORE-04)
 */
public interface EquipmentService {

    List<EquipmentResponse> findAll();

    List<EquipmentResponse> findAllActive();

    EquipmentResponse findById(Long id);

    Equipment findEntityById(Long id);

    EquipmentResponse create(EquipmentRequest request);

    EquipmentResponse update(Long id, EquipmentRequest request);

    void delete(Long id);
}
