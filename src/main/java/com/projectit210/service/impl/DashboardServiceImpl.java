package com.projectit210.service.impl;

import com.projectit210.enums.Role;
import com.projectit210.repository.BorrowingDetailRepository;
import com.projectit210.repository.BorrowingRecordRepository;
import com.projectit210.repository.MentoringSessionRepository;
import com.projectit210.repository.UserRepository;
import com.projectit210.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation của DashboardService
 * 
 * QUAN TRỌNG: Tất cả phép tính tổng/nhóm đều được thực hiện ở tầng DATABASE
 * thông qua các câu lệnh JPQL sử dụng JOIN, GROUP BY, HAVING, SUM, COUNT, ORDER BY
 * KHÔNG sử dụng vòng lặp for trong Java để tính toán tổng
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final BorrowingRecordRepository borrowingRecordRepository;
    private final BorrowingDetailRepository borrowingDetailRepository;
    private final MentoringSessionRepository mentoringSessionRepository;
    private final UserRepository userRepository;

    /**
     * Đếm số phiếu mượn đang ở trạng thái DISPATCHED (thiết bị đang được mượn)
     * Database thực hiện COUNT trực tiếp - không load dữ liệu vào Java
     */
    @Override
    public long countBorrowedEquipments() {
        return borrowingRecordRepository.countDispatched();
    }

    /**
     * Tổng số lượng thiết bị đang được mượn
     * Database thực hiện SUM trực tiếp - không dùng for-loop
     */
    @Override
    public long sumBorrowedEquipmentQuantity() {
        return borrowingRecordRepository.sumBorrowedEquipmentQuantity();
    }

    /**
     * Thống kê phiếu mượn theo trạng thái
     * Database thực hiện GROUP BY - Java chỉ chuyển đổi kết quả thành Map
     */
    @Override
    public Map<String, Long> getBorrowingStatsByStatus() {
        List<Object[]> results = borrowingRecordRepository.countGroupByStatus();
        Map<String, Long> stats = new LinkedHashMap<>();
        // Chỉ là ánh xạ kết quả từ DB, không phải tính toán bằng for-loop
        results.forEach(row -> stats.put(row[0].toString(), (Long) row[1]));
        return stats;
    }

    /**
     * Top 5 giảng viên có lượt tư vấn nhiều nhất
     * Sử dụng JOIN 3 bảng + GROUP BY + HAVING + ORDER BY + LIMIT
     * Tất cả xử lý bởi Database Engine
     */
    @Override
    public List<Object[]> getTop5Lecturers() {
        return mentoringSessionRepository.findTop5LecturersBySessionCount();
    }

    /**
     * Thống kê session theo trạng thái
     * Database thực hiện GROUP BY
     */
    @Override
    public Map<String, Long> getSessionStatsByStatus() {
        List<Object[]> results = mentoringSessionRepository.countGroupByStatus();
        Map<String, Long> stats = new LinkedHashMap<>();
        results.forEach(row -> stats.put(row[0].toString(), (Long) row[1]));
        return stats;
    }

    /**
     * Tổng số buổi tư vấn active (không bị hủy)
     */
    @Override
    public long countActiveSessions() {
        return mentoringSessionRepository.countActiveSessions();
    }

    /**
     * Đếm số sinh viên trực tiếp từ DB
     */
    @Override
    public long countStudents() {
        return userRepository.countByRole(Role.STUDENT);
    }

    /**
     * Đếm số giảng viên trực tiếp từ DB
     */
    @Override
    public long countLecturers() {
        return userRepository.countByRole(Role.LECTURER);
    }

    /**
     * Thống kê thiết bị đang mượn theo từng loại
     * Database thực hiện JOIN + GROUP BY + SUM
     */
    @Override
    public List<Object[]> getBorrowedByEquipmentStats() {
        return borrowingDetailRepository.countBorrowedByEquipment();
    }
}
