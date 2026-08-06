package com.ms.petopia.api.recruitnotice.dto.response;

import lombok.*;

// 모집 공고 상세 응답의 boothSlots 배열 항목 하나
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothSlotStatusResponse {

    private Long boothSlotsId; // booth_slots.booth_slot_id
    private String slotNumber;
    private Long price;
    private String status; // AVAILABLE(선택 가능) / PENDING(확정 대기) / CONFIRMED(확정 완료)

    private String businessName; // CONFIRMED일 때만 값 채워짐, 그 외엔 null

}
