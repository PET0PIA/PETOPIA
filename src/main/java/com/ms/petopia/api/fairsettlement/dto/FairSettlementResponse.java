package com.ms.petopia.api.fairsettlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 행사 하나의 최종정산 응답(플랫폼 ↔ 행사). 기존 SettlementResponse(사업자별 정산)와 달리
 * businessId가 없다 — 그 행사에 참가한 모든 사업자의 참가비를 합쳐 행사 하나로 정산한다.
 *
 * @param grossAmount       그 행사의 완료된 참가비 전체 합계(모든 참가업체 합산)
 * @param refundAmount      그 참가비 중 환불된 합계
 * @param commissionRate    정산 시점 요율 스냅샷
 * @param commissionAmount  (grossAmount - refundAmount) x commissionRate
 * @param netAmount         행사 지급액 = (grossAmount - refundAmount) - commissionAmount
 * @param status            PENDING(계산됨) / CONFIRMED(확정) / PAID(2차 범위)
 */
public record FairSettlementResponse(
        Long fairSettlementId,
        Long fairId,
        Long grossAmount,
        Long refundAmount,
        BigDecimal commissionRate,
        Long commissionAmount,
        Long netAmount,
        String status,
        LocalDateTime confirmedAt,
        Long confirmedByUserId
) {

    public static FairSettlementResponse from(FairSettlementRow row) {
        return new FairSettlementResponse(
                row.getFairSettlementId(),
                row.getFairId(),
                row.getGrossAmount(),
                row.getRefundAmount(),
                row.getCommissionRate(),
                row.getCommissionAmount(),
                row.getNetAmount(),
                row.getStatus(),
                row.getConfirmedAt(),
                row.getConfirmedByUserId()
        );
    }
}
