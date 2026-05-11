package com.projectit210.service.impl;

import com.projectit210.dto.request.DepartmentRequest;
import com.projectit210.entity.Department;
import com.projectit210.exception.BadRequestException;
import com.projectit210.repository.DepartmentRepository;
import com.projectit210.repository.LecturerRepository;
import com.projectit210.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final LecturerRepository lecturerRepository;

    @Override
    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    @Override
    public Department findById(Long id) {
        return departmentRepository.findById(id)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy Khoa/Ngành"));
    }

    @Override
    public Department create(DepartmentRequest request) {
        if (departmentRepository.existsByCode(request.getCode())) {
            throw new BadRequestException("Mã Khoa/Ngành đã tồn tại");
        }
        Department department = new Department();
        department.setCode(request.getCode());
        department.setName(request.getName());
        return departmentRepository.save(department);
    }

    @Override
    public Department update(Long id, DepartmentRequest request) {
        Department existing = findById(id);
        
        // Kiểm tra mã có bị trùng nếu đổi mã
        if (!existing.getCode().equals(request.getCode()) && departmentRepository.existsByCode(request.getCode())) {
            throw new BadRequestException("Mã Khoa/Ngành đã tồn tại");
        }
        
        existing.setCode(request.getCode());
        existing.setName(request.getName());
        return departmentRepository.save(existing);
    }

    @Override
    public void delete(Long id) {
        Department department = findById(id);
        
        // Kiểm tra xem có giảng viên nào thuộc khoa này không
        boolean hasLecturers = lecturerRepository.findAll().stream()
                .anyMatch(l -> l.getDepartment().getId().equals(id));
                
        if (hasLecturers) {
            throw new BadRequestException("Không thể xóa Khoa/Ngành đang có giảng viên");
        }
        
        departmentRepository.delete(department);
    }
}
