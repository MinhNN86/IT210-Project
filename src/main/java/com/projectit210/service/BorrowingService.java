package com.projectit210.service;

import com.projectit210.dto.response.BorrowingResponse;

import java.util.List;

/**
 * Service quản lý phiếu mượn thiết bị (CORE-08)
 */
public interface BorrowingService {

    /**
     * Xác nhận xuất kho - kiểm tra tồn kho & trừ stock (CORE-08) - Transaction
     */
    void approveDispatch(Long borrowingRecordId, String adminId);

    /**
     * Từ chối phiếu mượn
     */
    void rejectBorrowing(Long borrowingRecordId, String adminId);

    /**
     * Danh sách phiếu mượn chờ cấp phát
     */
    List<BorrowingResponse> getPendingDispatch();

    /**
     * Tất cả phiếu mượn
     */
    List<BorrowingResponse> getAllBorrowings();

    /**
     * Phiếu mượn của sinh viên
     */
    List<BorrowingResponse> getByStudent(String studentId);

    /**
     * Danh sách phiếu mượn đã xuất kho (đang mượn)
     */
    List<BorrowingResponse> getDispatched();

    /**
     * Xác nhận trả thiết bị - hoàn trả tồn kho & đổi trạng thái (CORE-08) - Transaction
     */
    void returnEquipment(Long borrowingRecordId, String adminId);
}
