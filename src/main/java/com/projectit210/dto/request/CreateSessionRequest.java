package com.projectit210.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * DTO đặt lịch cố vấn (CORE-05)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSessionRequest {

    @NotNull(message = "Khoa/Ngành không được để trống")
    private Long departmentId;

    @NotNull(message = "Giảng viên không được để trống")
    private Long lecturerId;

    @NotNull(message = "Ngày tư vấn không được để trống")
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate sessionDate;

    @NotNull(message = "Giờ bắt đầu không được để trống")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime;

    @NotNull(message = "Giờ kết thúc không được để trống")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime;

    private String note;
}
