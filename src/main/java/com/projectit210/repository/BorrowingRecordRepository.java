package com.projectit210.repository;

import com.projectit210.entity.BorrowingRecord;
import com.projectit210.enums.BorrowStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BorrowingRecordRepository extends JpaRepository<BorrowingRecord, Long> {

    List<BorrowingRecord> findByStudentId(String studentId);

    List<BorrowingRecord> findByStatus(BorrowStatus status);

    Optional<BorrowingRecord> findByMentoringSessionId(Long mentoringSessionId);

    @Query("SELECT b FROM BorrowingRecord b " +
           "JOIN FETCH b.student " +
           "JOIN FETCH b.mentoringSession s " +
           "JOIN FETCH s.lecturer l " +
           "JOIN FETCH l.user " +
           "LEFT JOIN FETCH b.details d " +
           "LEFT JOIN FETCH d.equipment " +
           "WHERE b.status = :status " +
           "ORDER BY b.createdAt DESC")
    List<BorrowingRecord> findByStatusWithDetails(@Param("status") BorrowStatus status);

    @Query("SELECT b FROM BorrowingRecord b " +
           "JOIN FETCH b.student " +
           "LEFT JOIN FETCH b.details d " +
           "LEFT JOIN FETCH d.equipment " +
           "ORDER BY b.createdAt DESC")
    List<BorrowingRecord> findAllWithDetails();

    // ===================== DASHBOARD STATISTICS QUERIES (Advanced SQL) =====================

    /**
     * Đếm số lượng phiếu mượn đang được mượn (trạng thái DISPATCHED)
     * Sử dụng JPQL trực tiếp để đếm từ database, không dùng for-loop
     */
    @Query("SELECT COUNT(b) FROM BorrowingRecord b WHERE b.status = 'DISPATCHED'")
    long countDispatched();

    /**
     * Thống kê số lượng phiếu mượn theo từng trạng thái (GROUP BY)
     * Trả về mảng Object[]: [status, count]
     */
    @Query("SELECT b.status, COUNT(b) FROM BorrowingRecord b GROUP BY b.status")
    List<Object[]> countGroupByStatus();

    /**
     * Tổng số lượng thiết bị đang được mượn (SUM + JOIN + GROUP BY)
     * JOIN borrowing_details để lấy tổng số lượng thiết bị đang ở trạng thái DISPATCHED
     */
    @Query("SELECT COALESCE(SUM(d.quantity), 0) " +
           "FROM BorrowingDetail d " +
           "JOIN d.borrowingRecord b " +
           "WHERE b.status = 'DISPATCHED'")
    long sumBorrowedEquipmentQuantity();
}
