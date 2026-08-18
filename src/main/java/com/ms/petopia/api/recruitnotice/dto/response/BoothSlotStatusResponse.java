package com.ms.petopia.api.recruitnotice.dto.response;

import lombok.*;

import java.math.BigDecimal;

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

    // 배치도 표시용 (0~1 비율 좌표, booth_slots와 동일 단위)
    private BigDecimal posX;
    private BigDecimal posY;
    private BigDecimal width;
    private BigDecimal height;

    /*
     * 배치도를 홀 단위로 묶어서 그리기 위한 정보. 한 행사에 홀이 여러 개면
     * posX/posY가 서로 다른 도면 기준이라 홀 구분 없이 한 배경에 그리면 안 됨.
     */
    private Long hallId;
    private String hallName;
    // 그 홀의 배치도 배경 이미지, 없으면 null
    private String floorPlanImageUrl;

}
