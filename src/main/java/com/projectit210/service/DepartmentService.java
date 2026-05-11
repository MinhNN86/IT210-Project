package com.projectit210.service;

import com.projectit210.dto.request.DepartmentRequest;
import com.projectit210.entity.Department;

import java.util.List;

public interface DepartmentService {
    List<Department> findAll();
    Department findById(Long id);
    Department create(DepartmentRequest request);
    Department update(Long id, DepartmentRequest request);
    void delete(Long id);
}
