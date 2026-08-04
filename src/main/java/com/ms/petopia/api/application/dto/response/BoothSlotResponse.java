package com.ms.petopia.api.application.dto.response;

import lombok.*;

import java.math.BigDecimal;

// GET /api/fairs/{fairId}/booth-slots 응답 (슬롯 하나)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothSlotResponse {

    private Long boothSlotsId; // booth_slots.booth_slot_id
    private String slotNumber;
    private BigDecimal posX;
    private BigDecimal posY;
    private BigDecimal width;
    private BigDecimal height;
    private Long price;
    private Boolean locked; // 활성 신청이 이 슬롯을 이미 선택했으면 true (우리 도메인이 판정)

}
