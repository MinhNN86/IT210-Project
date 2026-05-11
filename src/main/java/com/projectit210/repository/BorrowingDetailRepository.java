package com.projectit210.repository;

import com.projectit210.entity.BorrowingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BorrowingDetailRepository extends JpaRepository<BorrowingDetail, Long> {

    List<BorrowingDetail> findByBorrowingRecordId(Long borrowingRecordId);

    @Query("SELECT d FROM BorrowingDetail d " +
           "JOIN FETCH d.equipment " +
           "WHERE d.borrowingRecord.id = :recordId")
    List<BorrowingDetail> findByBorrowingRecordIdWithEquipment(@Param("recordId") Long recordId);

    // ===================== DASHBOARD STATISTICS QUERIES (Advanced SQL) =====================

    /**
     * Thống kê số lượng thiết bị đang được mượn theo từng loại (JOIN + GROUP BY)
     * Trả về mảng Object[]: [equipmentName, totalQuantity]
     *
     * SQL tương đương:
     * SELECT e.name, SUM(bd.quantity) AS total_quantity
     * FROM borrowing_details bd
     * INNER JOIN borrowing_records br ON bd.borrowing_record_id = br.id
     * INNER JOIN equipments e ON bd.equipment_id = e.id
     * WHERE br.status = 'DISPATCHED'
     * GROUP BY e.id, e.name
     * ORDER BY total_quantity DESC
     */
    @Query("SELECT e.name, SUM(d.quantity) " +
           "FROM BorrowingDetail d " +
           "JOIN d.borrowingRecord b " +
           "JOIN d.equipment e " +
           "WHERE b.status = 'DISPATCHED' " +
           "GROUP BY e.id, e.name " +
           "ORDER BY SUM(d.quantity) DESC")
    List<Object[]> countBorrowedByEquipment();
}
