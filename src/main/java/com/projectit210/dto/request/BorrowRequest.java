package com.projectit210.dto.request;

import lombok.*;

/**
 * DTO dự phòng cho luồng mượn thiết bị (sẽ mở rộng sau)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowRequest {
    private Long borrowingRecordId;
}
