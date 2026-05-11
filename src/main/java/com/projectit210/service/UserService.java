package com.projectit210.service;

import com.projectit210.entity.User;
import com.projectit210.enums.Role;

import java.util.List;
import java.util.Optional;

/**
 * Service quản lý người dùng
 */
public interface UserService {

    Optional<User> findById(String id);

    Optional<User> findByUsername(String username);

    List<User> findAll();

    List<User> findByRole(Role role);

    User save(User user);

    void deleteById(String id);
}
