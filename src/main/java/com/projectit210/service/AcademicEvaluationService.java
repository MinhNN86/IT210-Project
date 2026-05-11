package com.projectit210.service;

import com.projectit210.dto.request.EvaluationRequest;
import com.projectit210.dto.response.AcademicHistoryResponse;

import java.util.List;

/**
 * Service đánh giá học thuật (CORE-06, CORE-07)
 */
public interface AcademicEvaluationService {

    /**
     * Hoàn tất đánh giá + tạo phiếu mượn (CORE-06) - Transaction
     */
    void completeEvaluation(Long lecturerId, EvaluationRequest request);

    /**
     * Lịch sử học thuật liên kết (CORE-07) - JOIN data
     */
    List<AcademicHistoryResponse> getAcademicHistory(String studentId);
}
