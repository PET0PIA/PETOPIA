package com.ms.petopia.api.application.dto.response;

import lombok.*;

// 신청 상세 응답의 slots 배열 항목 하나
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationSlotDetailResponse {

    private Long boothSlotsId;
    private String slotNumber;
    private Long priceAtSelection; // 신청 시점 가격 스냅샷

}
