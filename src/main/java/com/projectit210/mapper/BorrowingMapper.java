package com.projectit210.mapper;

import com.projectit210.dto.response.AcademicHistoryResponse;
import com.projectit210.dto.response.BorrowingResponse;
import com.projectit210.entity.*;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper chuyển đổi BorrowingRecord/AcademicEvaluation → Response DTOs
 */
@Component
public class BorrowingMapper {

    public BorrowingResponse toResponse(BorrowingRecord record) {
        if (record == null) return null;

        List<BorrowingResponse.BorrowingDetailItem> detailItems = record.getDetails() != null
                ? record.getDetails().stream().map(this::toDetailItem).collect(Collectors.toList())
                : Collections.emptyList();

        return BorrowingResponse.builder()
                .id(record.getId())
                .mentoringSessionId(record.getMentoringSession().getId())
                .studentId(record.getStudent().getId())
                .studentName(UserMapper.getDisplayName(record.getStudent()))
                .status(record.getStatus().name())
                .borrowDate(record.getBorrowDate())
                .expectedReturnDate(record.getExpectedReturnDate())
                .actualReturnDate(record.getActualReturnDate())
                .details(detailItems)
                .build();
    }

    private BorrowingResponse.BorrowingDetailItem toDetailItem(BorrowingDetail detail) {
        return BorrowingResponse.BorrowingDetailItem.builder()
                .equipmentId(detail.getEquipment().getId())
                .equipmentCode(detail.getEquipment().getCode())
                .equipmentName(detail.getEquipment().getName())
                .quantity(detail.getQuantity())
                .currentStock(detail.getEquipment().getQuantityInStock())
                .build();
    }

    /**
     * Chuyển AcademicEvaluation + BorrowingRecord → AcademicHistoryResponse (CORE-07)
     */
    public AcademicHistoryResponse toAcademicHistory(AcademicEvaluation eval, BorrowingRecord record) {
        List<AcademicHistoryResponse.BorrowedEquipmentItem> equipments = Collections.emptyList();
        if (record != null && record.getDetails() != null) {
            equipments = record.getDetails().stream()
                    .map(d -> AcademicHistoryResponse.BorrowedEquipmentItem.builder()
                            .equipmentName(d.getEquipment().getName())
                            .equipmentCode(d.getEquipment().getCode())
                            .quantity(d.getQuantity())
                            .expectedReturnDate(record.getExpectedReturnDate())
                            .status(record.getStatus().name())
                            .build())
                    .collect(Collectors.toList());
        }

        return AcademicHistoryResponse.builder()
                .sessionId(eval.getMentoringSession().getId())
                .sessionDate(eval.getMentoringSession().getSessionDate())
                .lecturerName(UserMapper.getDisplayName(eval.getLecturer().getUser()))
                .departmentName(eval.getLecturer().getDepartment().getName())
                .performanceLevel(eval.getPerformanceLevel())
                .evaluationComment(eval.getEvaluationComment())
                .recommendation(eval.getRecommendation())
                .borrowedEquipments(equipments)
                .build();
    }
}
