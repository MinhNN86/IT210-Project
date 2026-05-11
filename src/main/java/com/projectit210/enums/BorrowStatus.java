package com.projectit210.enums;

/**
 * Trạng thái phiếu mượn thiết bị
 */
public enum BorrowStatus {
    PENDING_DISPATCH,  // Chờ cấp phát
    DISPATCHED,        // Đã xuất kho
    RETURNED,          // Đã trả
    REJECTED           // Bị từ chối
}
