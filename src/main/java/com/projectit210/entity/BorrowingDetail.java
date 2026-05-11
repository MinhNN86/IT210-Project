package com.projectit210.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Entity ánh xạ bảng borrowing_details - Chi tiết phiếu mượn (quan hệ N-N giữa phiếu mượn và thiết bị)
 */
@Entity
@Table(name = "borrowing_details")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrowing_record_id", nullable = false)
    private BorrowingRecord borrowingRecord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "equipment_id", nullable = false)
    private Equipment equipment;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;
}
