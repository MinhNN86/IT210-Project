package com.projectit210.service.impl;

import com.projectit210.dto.request.EvaluationRequest;
import com.projectit210.dto.response.AcademicHistoryResponse;
import com.projectit210.entity.*;
import com.projectit210.enums.BorrowStatus;
import com.projectit210.enums.SessionStatus;
import com.projectit210.exception.BadRequestException;
import com.projectit210.exception.ResourceNotFoundException;
import com.projectit210.mapper.BorrowingMapper;
import com.projectit210.repository.*;
import com.projectit210.service.AcademicEvaluationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcademicEvaluationServiceImpl implements AcademicEvaluationService {

    private final MentoringSessionRepository sessionRepository;
    private final AcademicEvaluationRepository evaluationRepository;
    private final BorrowingRecordRepository borrowingRecordRepository;
    private final BorrowingDetailRepository borrowingDetailRepository;
    private final LecturerRepository lecturerRepository;
    private final EquipmentRepository equipmentRepository;
    private final BorrowingMapper borrowingMapper;

    /**
     * CORE-06: Hoàn tất đánh giá - Transaction đảm bảo toàn vẹn dữ liệu
     * 1. Cập nhật session status → COMPLETED
     * 2. Lưu AcademicEvaluation
     * 3. Tạo BorrowingRecord + BorrowingDetails
     * Nếu bất kỳ bước nào fail → Rollback toàn bộ
     */
    @Override
    @Transactional
    public void completeEvaluation(Long lecturerId, EvaluationRequest request) {
        MentoringSession session = sessionRepository.findById(request.getMentoringSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Buổi tư vấn không tồn tại"));

        if (session.getStatus() != SessionStatus.CONFIRMED) {
            throw new BadRequestException("Buổi tư vấn phải ở trạng thái 'Đã xác nhận' mới có thể đánh giá");
        }

        Lecturer lecturer = lecturerRepository.findById(lecturerId)
                .orElseThrow(() -> new ResourceNotFoundException("Giảng viên không tồn tại"));

        // Bước 1: Cập nhật trạng thái session → COMPLETED
        session.setStatus(SessionStatus.COMPLETED);
        sessionRepository.save(session);

        // Bước 2: Lưu đánh giá
        AcademicEvaluation evaluation = AcademicEvaluation.builder()
                .mentoringSession(session)
                .lecturer(lecturer)
                .student(session.getStudent())
                .performanceLevel(request.getPerformanceLevel())
                .evaluationComment(request.getEvaluationComment())
                .recommendation(request.getRecommendation())
                .build();
        evaluationRepository.save(evaluation);

        // Bước 3: Tạo phiếu mượn chỉ khi có ít nhất 1 thiết bị với số lượng > 0
        boolean hasValidEquipment = request.getEquipmentItems() != null &&
                request.getEquipmentItems().stream()
                        .anyMatch(item -> item.getEquipmentId() != null &&
                                item.getQuantity() != null && item.getQuantity() > 0);

        if (hasValidEquipment) {
            BorrowingRecord record = BorrowingRecord.builder()
                    .mentoringSession(session)
                    .student(session.getStudent())
                    .status(BorrowStatus.PENDING_DISPATCH)
                    .expectedReturnDate(request.getExpectedReturnDate())
                    .details(new ArrayList<>())
                    .build();
            record = borrowingRecordRepository.save(record);

            for (EvaluationRequest.EquipmentItem item : request.getEquipmentItems()) {
                if (item.getEquipmentId() == null || item.getQuantity() == null || item.getQuantity() <= 0) continue;

                Equipment equipment = equipmentRepository.findById(item.getEquipmentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Thiết bị không tồn tại"));

                BorrowingDetail detail = BorrowingDetail.builder()
                        .borrowingRecord(record)
                        .equipment(equipment)
                        .quantity(item.getQuantity())
                        .build();
                borrowingDetailRepository.save(detail);
            }
        }
    }

    /**
     * CORE-07: Tra cứu Hồ sơ Học thuật Liên kết - JOIN phức tạp
     */
    @Override
    public List<AcademicHistoryResponse> getAcademicHistory(String studentId) {
        List<AcademicEvaluation> evaluations = evaluationRepository.findByStudentIdWithDetails(studentId);

        return evaluations.stream().map(eval -> {
            BorrowingRecord record = borrowingRecordRepository
                    .findByMentoringSessionId(eval.getMentoringSession().getId())
                    .orElse(null);

            if (record != null) {
                List<BorrowingDetail> details = borrowingDetailRepository
                        .findByBorrowingRecordIdWithEquipment(record.getId());
                record.setDetails(details);
            }

            return borrowingMapper.toAcademicHistory(eval, record);
        }).collect(Collectors.toList());
    }
}
