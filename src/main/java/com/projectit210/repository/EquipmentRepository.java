package com.projectit210.repository;

import com.projectit210.entity.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    List<Equipment> findByIsActiveTrue();

    Optional<Equipment> findByCode(String code);

    boolean existsByCode(String code);
}
