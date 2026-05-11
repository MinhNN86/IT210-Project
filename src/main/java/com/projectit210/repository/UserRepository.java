package com.projectit210.repository;

import com.projectit210.entity.User;
import com.projectit210.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameAndIdNot(String username, String id);

    boolean existsByEmailAndIdNot(String email, String id);

    List<User> findByRole(Role role);

    List<User> findByIsActiveTrue();

    // ===================== DASHBOARD STATISTICS QUERIES =====================

    /**
     * Đếm số lượng user theo role trực tiếp từ database (không cần load tất cả records)
     */
    long countByRole(Role role);
}
