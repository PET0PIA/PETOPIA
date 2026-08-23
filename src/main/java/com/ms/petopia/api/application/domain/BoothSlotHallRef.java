package com.ms.petopia.api.application.domain;

import lombok.*;

// 부스슬롯 잠금/해제(fair 도메인 BoothSlotService) 연동 전용 내부 쌍(pair) 객체. API로 노출하지 않는다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothSlotHallRef {

    private Long boothSlotId;
    private Long hallId;

}
