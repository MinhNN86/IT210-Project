package com.projectit210.service;

/**
 * Service quản lý tồn kho thiết bị
 */
public interface InventoryService {

    /**
     * Kiểm tra tồn kho có đủ không
     */
    boolean checkStock(Long equipmentId, int quantity);

    /**
     * Trừ tồn kho
     */
    void reduceStock(Long equipmentId, int quantity);

    /**
     * Hoàn trả tồn kho
     */
    void restoreStock(Long equipmentId, int quantity);
}
