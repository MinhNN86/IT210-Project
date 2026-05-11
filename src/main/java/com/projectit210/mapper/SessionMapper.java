package com.projectit210.mapper;

import com.projectit210.dto.response.SessionResponse;
import com.projectit210.entity.MentoringSession;
import com.projectit210.enums.SessionStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Mapper chuyển đổi MentoringSession entity → SessionResponse DTO
 */
@Component
public class SessionMapper {

    public SessionResponse toResponse(MentoringSession session) {
        if (session == null) return null;

        // Kiểm tra có thể hủy: trước 24 giờ so với thời điểm tư vấn và trạng thái PENDING
        boolean canCancel = false;
        if (session.getStatus() == SessionStatus.PENDING) {
            LocalDateTime sessionDateTime = LocalDateTime.of(session.getSessionDate(), session.getStartTime());
            canCancel = sessionDateTime.minusHours(24).isAfter(LocalDateTime.now());
        }

        return SessionResponse.builder()
                .id(session.getId())
                .studentId(session.getStudent().getId())
                .studentName(UserMapper.getDisplayName(session.getStudent()))
                .lecturerId(session.getLecturer().getId())
                .lecturerName(UserMapper.getDisplayName(session.getLecturer().getUser()))
                .departmentName(session.getLecturer().getDepartment().getName())
                .sessionDate(session.getSessionDate())
                .startTime(session.getStartTime())
                .endTime(session.getEndTime())
                .status(session.getStatus().name())
                .note(session.getNote())
                .canCancel(canCancel)
                .build();
    }
}
