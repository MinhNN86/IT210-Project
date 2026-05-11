package com.projectit210.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO đánh giá năng lực + chỉ định thiết bị cho sinh viên (CORE-06)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EvaluationRequest {

    @NotNull(message = "Mã buổi tư vấn không được để trống")
    private Long mentoringSessionId;

    private String performanceLevel;

    private String evaluationComment;

    private String recommendation;

    /**
     * Danh sách thiết bị cần cấp cho sinh viên
     */
    private List<EquipmentItem> equipmentItems;

    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate expectedReturnDate;

    /**
     * Inner class đại diện cho 1 dòng thiết bị: id + số lượng
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EquipmentItem {
        private Long equipmentId;
        private Integer quantity;
    }
}
