package com.ms.petopia.api.fair.dto;

import java.math.BigDecimal;

/**
 * 부스 슬롯 일괄저장 요청의 개별 항목.
 *
 * <p>{@code boothSlotId}가 있으면 기존 슬롯 수정, 없으면 신규 슬롯 생성으로 처리한다.
 * 요청에 포함되지 않은 기존 슬롯은 삭제 대상이 된다(단, locked_at이 있으면 삭제 불가).
 */
public record BoothSlotItem(
        Long boothSlotId,
        String slotNumber,
        BigDecimal posX,
        BigDecimal posY,
        BigDecimal width,
        BigDecimal height,
        Long price,
        String memo
) {
}
