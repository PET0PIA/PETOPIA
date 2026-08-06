package com.ms.petopia.api.application.domain;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationSlot {

    private Long applicationSlotId;
    private Long applicationId;
    private Long boothSlotId; // 부스슬롯 참조
    private Long priceAtSelection; // 신청 시점 가격 스냅샷

}
