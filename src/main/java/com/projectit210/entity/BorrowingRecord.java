package com.projectit210.entity;

import com.projectit210.enums.BorrowStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity ánh xạ bảng borrowing_records - Phiếu mượn thiết bị tổng thể
 */
@Entity
@Table(name = "borrowing_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mentoring_session_id", unique = true, nullable = false)
    private MentoringSession mentoringSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private BorrowStatus status = BorrowStatus.PENDING_DISPATCH;

    @Column(name = "borrow_date")
    private LocalDateTime borrowDate;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Column(name = "actual_return_date")
    private LocalDate actualReturnDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "borrowingRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<BorrowingDetail> details = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.borrowDate = LocalDateTime.now();
    }
}
