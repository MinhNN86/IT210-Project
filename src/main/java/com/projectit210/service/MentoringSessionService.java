package com.projectit210.service;

import com.projectit210.dto.request.CreateSessionRequest;
import com.projectit210.dto.response.SessionResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Service quản lý buổi tư vấn (CORE-05, CORE-09)
 */
public interface MentoringSessionService {

    /**
     * Đặt lịch cố vấn - kiểm tra xung đột & ngày quá khứ (CORE-05)
     */
    SessionResponse createSession(String studentId, CreateSessionRequest request);

    /**
     * Hủy lịch - kiểm tra thời gian >= 24h trước buổi tư vấn (CORE-09)
     */
    void cancelSession(Long sessionId, String studentId);

    /**
     * Danh sách sessions của sinh viên
     */
    List<SessionResponse> getSessionsByStudent(String studentId);

    /**
     * Danh sách sessions pending của giảng viên
     */
    List<SessionResponse> getPendingSessionsByLecturer(Long lecturerId);

    /**
     * Giảng viên hủy buổi tư vấn kèm lý do
     */
    void cancelSessionByLecturer(Long sessionId, Long lecturerId, String reason);

    /**
     * Giảng viên xác nhận lịch hẹn PENDING → CONFIRMED
     */
    void confirmSession(Long sessionId, Long lecturerId);

    /**
     * Danh sách sessions confirmed của giảng viên (chờ đánh giá)
     */
    List<SessionResponse> getConfirmedSessionsByLecturer(Long lecturerId);

    /**
     * Lấy danh sách slot đã đặt trong ngày
     */
    List<LocalTime> getBookedSlots(Long lecturerId, LocalDate date);
}
