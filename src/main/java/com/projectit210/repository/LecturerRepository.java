package com.projectit210.repository;

import com.projectit210.entity.Lecturer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LecturerRepository extends JpaRepository<Lecturer, Long> {

    List<Lecturer> findByDepartmentId(Long departmentId);

    Optional<Lecturer> findByUserId(String userId);

    @Query("SELECT l FROM Lecturer l JOIN FETCH l.user JOIN FETCH l.department WHERE l.department.id = :deptId")
    List<Lecturer> findByDepartmentIdWithDetails(@Param("deptId") Long departmentId);

    @Query("SELECT l FROM Lecturer l JOIN FETCH l.user JOIN FETCH l.department")
    List<Lecturer> findAllWithDetails();
}
