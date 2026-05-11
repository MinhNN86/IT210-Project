package com.projectit210.dto.response;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO hiển thị phiếu mượn thiết bị
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowingResponse {
    private Long id;
    private Long mentoringSessionId;
    private String studentId;
    private String studentName;
    private String status;
    private LocalDateTime borrowDate;
    private LocalDate expectedReturnDate;
    private LocalDate actualReturnDate;
    private List<BorrowingDetailItem> details;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class BorrowingDetailItem {
        private Long equipmentId;
        private String equipmentCode;
        private String equipmentName;
        private Integer quantity;
        private Integer currentStock; // Tồn kho hiện tại để Admin kiểm tra
    }
}
