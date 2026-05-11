package com.projectit210.service.impl;

import com.projectit210.dto.response.BorrowingResponse;
import com.projectit210.entity.BorrowingDetail;
import com.projectit210.entity.BorrowingRecord;
import com.projectit210.entity.User;
import com.projectit210.enums.BorrowStatus;
import com.projectit210.exception.BadRequestException;
import com.projectit210.exception.ResourceNotFoundException;
import com.projectit210.mapper.BorrowingMapper;
import com.projectit210.repository.BorrowingDetailRepository;
import com.projectit210.repository.BorrowingRecordRepository;
import com.projectit210.repository.UserRepository;
import com.projectit210.service.BorrowingService;
import com.projectit210.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CORE-08: Cấp phát Thiết bị & Quản lý Tồn kho
 */
@Service
@RequiredArgsConstructor
public class BorrowingServiceImpl implements BorrowingService {

    private final BorrowingRecordRepository borrowingRecordRepository;
    private final BorrowingDetailRepository borrowingDetailRepository;
    private final UserRepository userRepository;
    private final InventoryService inventoryService;
    private final BorrowingMapper borrowingMapper;

    @Override
    @Transactional
    public void approveDispatch(Long borrowingRecordId, String adminId) {
        BorrowingRecord record = borrowingRecordRepository.findById(borrowingRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu mượn không tồn tại"));

        if (record.getStatus() != BorrowStatus.PENDING_DISPATCH) {
            throw new BadRequestException("Phiếu mượn không ở trạng thái chờ cấp phát");
        }

        List<BorrowingDetail> details = borrowingDetailRepository
                .findByBorrowingRecordIdWithEquipment(borrowingRecordId);

        // Kiểm tra tồn kho TOÀN BỘ trước khi trừ
        for (BorrowingDetail detail : details) {
            if (!inventoryService.checkStock(detail.getEquipment().getId(), detail.getQuantity())) {
                throw new BadRequestException(
                        String.format("Không đủ tồn kho cho thiết bị '%s' (Cần: %d, Còn: %d)",
                                detail.getEquipment().getName(),
                                detail.getQuantity(),
                                detail.getEquipment().getQuantityInStock()));
            }
        }

        // Trừ tồn kho
        for (BorrowingDetail detail : details) {
            inventoryService.reduceStock(detail.getEquipment().getId(), detail.getQuantity());
        }

        // Cập nhật trạng thái
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin không tồn tại"));
        record.setApprovedBy(admin);
        record.setStatus(BorrowStatus.DISPATCHED);
        borrowingRecordRepository.save(record);
    }

    @Override
    @Transactional
    public void rejectBorrowing(Long borrowingRecordId, String adminId) {
        BorrowingRecord record = borrowingRecordRepository.findById(borrowingRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu mượn không tồn tại"));

        if (record.getStatus() != BorrowStatus.PENDING_DISPATCH) {
            throw new BadRequestException("Phiếu mượn không ở trạng thái chờ cấp phát");
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin không tồn tại"));
        record.setApprovedBy(admin);
        record.setStatus(BorrowStatus.REJECTED);
        borrowingRecordRepository.save(record);
    }

    @Override
    public List<BorrowingResponse> getPendingDispatch() {
        return borrowingRecordRepository.findByStatusWithDetails(BorrowStatus.PENDING_DISPATCH)
                .stream().map(borrowingMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<BorrowingResponse> getAllBorrowings() {
        return borrowingRecordRepository.findAllWithDetails()
                .stream().map(borrowingMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<BorrowingResponse> getByStudent(String studentId) {
        return borrowingRecordRepository.findByStudentId(studentId)
                .stream().map(borrowingMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public List<BorrowingResponse> getDispatched() {
        return borrowingRecordRepository.findByStatusWithDetails(BorrowStatus.DISPATCHED)
                .stream().map(borrowingMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void returnEquipment(Long borrowingRecordId, String adminId) {
        BorrowingRecord record = borrowingRecordRepository.findById(borrowingRecordId)
                .orElseThrow(() -> new ResourceNotFoundException("Phiếu mượn không tồn tại"));

        if (record.getStatus() != BorrowStatus.DISPATCHED) {
            throw new BadRequestException("Phiếu mượn không ở trạng thái đã xuất kho");
        }

        // Hoàn trả tồn kho cho tất cả thiết bị trong phiếu mượn
        List<BorrowingDetail> details = borrowingDetailRepository
                .findByBorrowingRecordIdWithEquipment(borrowingRecordId);

        for (BorrowingDetail detail : details) {
            inventoryService.restoreStock(detail.getEquipment().getId(), detail.getQuantity());
        }

        // Cập nhật trạng thái và ngày trả thực tế
        record.setStatus(BorrowStatus.RETURNED);
        record.setActualReturnDate(LocalDate.now());
        borrowingRecordRepository.save(record);
    }
}
