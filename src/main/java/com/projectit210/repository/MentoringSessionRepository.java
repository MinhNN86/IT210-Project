package com.projectit210.repository;

import com.projectit210.entity.MentoringSession;
import com.projectit210.enums.SessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface MentoringSessionRepository extends JpaRepository<MentoringSession, Long> {

    /**
     * Kiểm tra xung đột slot: giảng viên đã có lịch chồng chéo trong cùng ngày chưa (chỉ tính các trạng thái active)
     * Hai khoảng thời gian [start1, end1) và [start2, end2) overlap khi: start1 < end2 AND end1 > start2
     */
    @Query("SELECT COUNT(s) > 0 FROM MentoringSession s " +
           "WHERE s.lecturer.id = :lecturerId " +
           "AND s.sessionDate = :sessionDate " +
           "AND s.startTime < :endTime " +
           "AND s.endTime > :startTime " +
           "AND s.status <> 'CANCELLED'")
    boolean existsConflictingSlot(@Param("lecturerId") Long lecturerId,
                                  @Param("sessionDate") LocalDate sessionDate,
                                  @Param("startTime") LocalTime startTime,
                                  @Param("endTime") LocalTime endTime);

    List<MentoringSession> findByStudentIdOrderBySessionDateDescStartTimeDesc(String studentId);

    List<MentoringSession> findByLecturerIdOrderBySessionDateDescStartTimeDesc(Long lecturerId);

    List<MentoringSession> findByLecturerIdAndStatus(Long lecturerId, SessionStatus status);

    @Query("SELECT s FROM MentoringSession s " +
           "JOIN FETCH s.student " +
           "JOIN FETCH s.lecturer l " +
           "JOIN FETCH l.user " +
           "JOIN FETCH l.department " +
           "WHERE s.student.id = :studentId " +
           "ORDER BY s.sessionDate DESC, s.startTime DESC")
    List<MentoringSession> findByStudentIdWithDetails(@Param("studentId") String studentId);

    @Query("SELECT s FROM MentoringSession s " +
           "JOIN FETCH s.student " +
           "JOIN FETCH s.lecturer l " +
           "JOIN FETCH l.user " +
           "WHERE s.lecturer.id = :lecturerId AND s.status = :status " +
           "ORDER BY s.sessionDate ASC, s.startTime ASC")
    List<MentoringSession> findByLecturerIdAndStatusWithDetails(@Param("lecturerId") Long lecturerId,
                                                                 @Param("status") SessionStatus status);

    /**
     * Lấy các slot đã được đặt của giảng viên trong 1 ngày (chỉ tính active, không tính CANCELLED)
     */
    @Query("SELECT s FROM MentoringSession s " +
           "WHERE s.lecturer.id = :lecturerId " +
           "AND s.sessionDate = :sessionDate " +
           "AND s.status <> 'CANCELLED'")
    List<MentoringSession> findBookedSlots(@Param("lecturerId") Long lecturerId,
                                           @Param("sessionDate") LocalDate sessionDate);

    // ===================== DASHBOARD STATISTICS QUERIES (Advanced SQL) =====================

    /**
     * Top 5 giảng viên có lượt tư vấn nhiều nhất
     * Sử dụng JOIN nhiều bảng (mentoring_sessions → lecturers → users) + GROUP BY + HAVING + ORDER BY
     * Trả về mảng Object[]: [lecturerName, sessionCount]
     *
     * SQL tương đương:
     * SELECT u.full_name, COUNT(ms.id) AS session_count
     * FROM mentoring_sessions ms
     * INNER JOIN lecturers l ON ms.lecturer_id = l.id
     * INNER JOIN users u ON l.user_id = u.id
     * WHERE ms.status <> 'CANCELLED'
     * GROUP BY l.id, u.full_name
     * HAVING COUNT(ms.id) > 0
     * ORDER BY session_count DESC
     * LIMIT 5
     */
    @Query("SELECT u.fullName, COUNT(s) " +
           "FROM MentoringSession s " +
           "JOIN s.lecturer l " +
           "JOIN l.user u " +
           "WHERE s.status <> 'CANCELLED' " +
           "GROUP BY l.id, u.fullName " +
           "HAVING COUNT(s) > 0 " +
           "ORDER BY COUNT(s) DESC " +
           "LIMIT 5")
    List<Object[]> findTop5LecturersBySessionCount();

    /**
     * Thống kê số lượng session theo từng trạng thái (GROUP BY)
     * Trả về mảng Object[]: [status, count]
     */
    @Query("SELECT s.status, COUNT(s) FROM MentoringSession s GROUP BY s.status")
    List<Object[]> countGroupByStatus();

    /**
     * Tổng số buổi tư vấn (không bị hủy)
     */
    @Query("SELECT COUNT(s) FROM MentoringSession s WHERE s.status <> 'CANCELLED'")
    long countActiveSessions();
}
