package com.ms.petopia.api.fair.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 부스 슬롯 조회/일괄저장 응답.
 *
 * @param lockedAt null이 아니면 위치·번호·가격·삭제가 제한된 슬롯이다(참가 신청이 걸려 있음).
 */
public record BoothSlotResponse(
        Long boothSlotId,
        Long hallId,
        String slotNumber,
        BigDecimal posX,
        BigDecimal posY,
        BigDecimal width,
        BigDecimal height,
        Long price,
        Boolean active,
        String memo,
        LocalDateTime lockedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
