package com.projectit210.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO hiển thị thông tin buổi tư vấn
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionResponse {
    private Long id;
    private String studentId;
    private String studentName;
    private Long lecturerId;
    private String lecturerName;
    private String departmentName;
    private LocalDate sessionDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String note;
    private boolean canCancel; // true nếu có thể hủy (trước 24h)
    private Boolean meetingActive;
}
