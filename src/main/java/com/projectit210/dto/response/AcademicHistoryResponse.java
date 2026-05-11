package com.projectit210.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.util.List;

/**
 * DTO hiển thị lịch sử học thuật liên kết (CORE-07)
 * Gồm thông tin giảng viên, đánh giá, và danh sách thiết bị đã mượn
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AcademicHistoryResponse {
    private Long sessionId;
    private LocalDate sessionDate;
    private String lecturerName;
    private String departmentName;
    private String performanceLevel;
    private String evaluationComment;
    private String recommendation;
    private List<BorrowedEquipmentItem> borrowedEquipments;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BorrowedEquipmentItem {
        private String equipmentName;
        private String equipmentCode;
        private Integer quantity;
        private LocalDate expectedReturnDate;
        private String status;
    }
}
