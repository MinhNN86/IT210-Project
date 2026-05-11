package com.projectit210.service;

import java.util.List;
import java.util.Map;

/**
 * Service cung cấp dữ liệu thống kê cho Admin Dashboard
 * Tất cả phép tính tổng/nhóm đều thực hiện ở tầng database bằng SQL nâng cao (JOIN, GROUP BY, HAVING)
 * Không sử dụng vòng lặp for trong Java để tính toán tổng
 */
public interface DashboardService {

    /**
     * Đếm số lượng thiết bị đang được mượn (status = DISPATCHED)
     * Sử dụng JPQL: SELECT COUNT(b) FROM BorrowingRecord b WHERE b.status = 'DISPATCHED'
     */
    long countBorrowedEquipments();

    /**
     * Tổng số lượng (quantity) thiết bị đang được mượn
     * Sử dụng JPQL: SELECT COALESCE(SUM(d.quantity), 0) FROM BorrowingDetail d JOIN d.borrowingRecord b WHERE b.status = 'DISPATCHED'
     */
    long sumBorrowedEquipmentQuantity();

    /**
     * Thống kê số lượng phiếu mượn theo trạng thái (GROUP BY)
     * Trả về Map<status, count>
     */
    Map<String, Long> getBorrowingStatsByStatus();

    /**
     * Top 5 giảng viên có lượt tư vấn nhiều nhất
     * Sử dụng JOIN + GROUP BY + HAVING + ORDER BY trực tiếp trong SQL
     * Trả về List<Object[]>: [lecturerName, sessionCount]
     */
    List<Object[]> getTop5Lecturers();

    /**
     * Thống kê số lượng session theo trạng thái (GROUP BY)
     * Trả về Map<status, count>
     */
    Map<String, Long> getSessionStatsByStatus();

    /**
     * Tổng số buổi tư vấn (không bị hủy)
     */
    long countActiveSessions();

    /**
     * Tổng số sinh viên (đếm trực tiếp từ DB)
     */
    long countStudents();

    /**
     * Tổng số giảng viên (đếm trực tiếp từ DB)
     */
    long countLecturers();

    /**
     * Thống kê thiết bị đang được mượn theo từng loại (JOIN + GROUP BY)
     * Trả về List<Object[]>: [equipmentName, totalQuantity]
     */
    List<Object[]> getBorrowedByEquipmentStats();
}
