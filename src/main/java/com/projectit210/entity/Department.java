package com.projectit210.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity ánh xạ bảng departments - Dữ liệu nền Khoa/Ngành (seed data)
 */
@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", length = 20, unique = true, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;
}
