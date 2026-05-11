package com.projectit210.service.impl;

import com.projectit210.dto.request.CreateSessionRequest;
import com.projectit210.dto.response.SessionResponse;
import com.projectit210.entity.Lecturer;
import com.projectit210.entity.MentoringSession;
import com.projectit210.entity.User;
import com.projectit210.enums.SessionStatus;
import com.projectit210.exception.BadRequestException;
import com.projectit210.exception.ConflictException;
import com.projectit210.exception.ResourceNotFoundException;
import com.projectit210.mapper.SessionMapper;
import com.projectit210.repository.LecturerRepository;
import com.projectit210.repository.MentoringSessionRepository;
import com.projectit210.repository.UserRepository;
import com.projectit210.service.MentoringSessionService;
import com.projectit210.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Triển khai MentoringSessionService (CORE-05, CORE-09)
 */
@Service
@RequiredArgsConstructor
public class MentoringSessionServiceImpl implements MentoringSessionService {

    private final MentoringSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final LecturerRepository lecturerRepository;
    private final SessionMapper sessionMapper;

    @Override
    @Transactional
    public SessionResponse createSession(String studentId, CreateSessionRequest request) {
        // Validate: không cho đặt lịch trong quá khứ (CORE-05)
        if (DateUtil.isPast(request.getSessionDate(), request.getStartTime())) {
            throw new BadRequestException("Không thể đặt lịch vào ngày/giờ trong quá khứ");
        }

        // Validate: giờ kết thúc phải sau giờ bắt đầu
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BadRequestException("Giờ kết thúc phải sau giờ bắt đầu");
        }

        // Kiểm tra giảng viên tồn tại
        Lecturer lecturer = lecturerRepository.findById(request.getLecturerId())
                .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));

        // Kiểm tra xung đột slot (CORE-05: chống duplicate & overlap)
        boolean conflict = sessionRepository.existsConflictingSlot(
                request.getLecturerId(),
                request.getSessionDate(),
                request.getStartTime(),
                request.getEndTime()
        );
        if (conflict) {
            throw new ConflictException("Giảng viên đã có lịch trong khung giờ này. Vui lòng chọn khung giờ khác.");
        }

        // Lấy thông tin sinh viên
        User student = userRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Sinh viên không tồn tại"));

        // Tạo session mới với trạng thái PENDING
        MentoringSession session = MentoringSession.builder()
                .student(student)
                .lecturer(lecturer)
                .sessionDate(request.getSessionDate())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .status(SessionStatus.PENDING)
                .note(request.getNote())
                .build();

        session = sessionRepository.save(session);
        return sessionMapper.toResponse(session);
    }

    @Override
    @Transactional
    public void cancelSession(Long sessionId, String studentId) {
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi tư vấn không tồn tại"));

        // Kiểm tra quyền: chỉ sinh viên đã đặt mới được hủy
        if (!session.getStudent().getId().equals(studentId)) {
            throw new BadRequestException("Bạn không có quyền hủy buổi tư vấn này");
        }

        // Chỉ hủy được trạng thái PENDING
        if (session.getStatus() != SessionStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể hủy buổi tư vấn đang ở trạng thái 'Chờ xác nhận'");
        }

        // Kiểm tra thời gian: phải trước 24 giờ (CORE-09)
        LocalDateTime sessionDateTime = LocalDateTime.of(session.getSessionDate(), session.getStartTime());
        if (sessionDateTime.minusHours(24).isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Chỉ có thể hủy lịch trước 24 giờ so với thời điểm tư vấn");
        }

        // Cập nhật trạng thái → CANCELLED, ghi nhận thời gian hủy
        session.setStatus(SessionStatus.CANCELLED);
        session.setCancelledAt(LocalDateTime.now());
        sessionRepository.save(session);
        // Slot tự động được giải phóng vì query existsConflictingSlot loại trừ status CANCELLED
    }

    @Override
    public List<SessionResponse> getSessionsByStudent(String studentId) {
        return sessionRepository.findByStudentIdWithDetails(studentId).stream()
                .map(sessionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void cancelSessionByLecturer(Long sessionId, Long lecturerId, String reason) {
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi tư vấn không tồn tại"));

        if (!session.getLecturer().getId().equals(lecturerId)) {
            throw new BadRequestException("Bạn không có quyền hủy buổi tư vấn này");
        }

        if (session.getStatus() != SessionStatus.PENDING && session.getStatus() != SessionStatus.CONFIRMED) {
            throw new BadRequestException("Chỉ có thể hủy buổi tư vấn đang ở trạng thái 'Chờ xác nhận' hoặc 'Đã xác nhận'");
        }

        session.setStatus(SessionStatus.CANCELLED);
        session.setCancelReason(reason);
        session.setCancelledAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    @Override
    @Transactional
    public void confirmSession(Long sessionId, Long lecturerId) {
        MentoringSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Buổi tư vấn không tồn tại"));

        if (!session.getLecturer().getId().equals(lecturerId)) {
            throw new BadRequestException("Bạn không có quyền xác nhận buổi tư vấn này");
        }

        if (session.getStatus() != SessionStatus.PENDING) {
            throw new BadRequestException("Chỉ có thể xác nhận buổi tư vấn đang ở trạng thái 'Chờ xác nhận'");
        }

        session.setStatus(SessionStatus.CONFIRMED);
        sessionRepository.save(session);
    }

    @Override
    public List<SessionResponse> getPendingSessionsByLecturer(Long lecturerId) {
        return sessionRepository.findByLecturerIdAndStatusWithDetails(lecturerId, SessionStatus.PENDING).stream()
                .map(sessionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<SessionResponse> getConfirmedSessionsByLecturer(Long lecturerId) {
        return sessionRepository.findByLecturerIdAndStatusWithDetails(lecturerId, SessionStatus.CONFIRMED).stream()
                .map(sessionMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<LocalTime> getBookedSlots(Long lecturerId, LocalDate date) {
        return sessionRepository.findBookedSlots(lecturerId, date).stream()
                .map(MentoringSession::getStartTime)
                .collect(Collectors.toList());
    }
}
