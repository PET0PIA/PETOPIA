package com.ms.petopia.api.commisionrate.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 조회 응답. DB에 한 번도 설정된 적 없으면(부트스트랩 기본값 사용 중)
 * updatedAt/updatedByUserId가 null로 내려간다 — FE가 "기본값 적용 중" 표시하는 용도.
 */


public record CommissionRateResponse(
        String scope,
        Long fairId,
        BigDecimal rate,
        Long updatedByUserId,
        LocalDateTime updatedAt
) {

    public static CommissionRateResponse from(CommissionRateRow row) {
        return new CommissionRateResponse(
                row.getScope(), row.getFairId(), row.getRate(),
                row.getUpdatedByUserId(), row.getUpdatedAt()
        );
    }

    public static CommissionRateResponse bootstrapDefault(BigDecimal rate) {
        return new CommissionRateResponse("GLOBAL", null, rate, null, null);
    }




}
