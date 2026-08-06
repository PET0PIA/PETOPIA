package com.ms.petopia.api.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 정산 단건 응답.
 *
 * @param grossAmount       완료된 참가비 합계
 * @param refundAmount      그 참가비 중 환불된 합계
 * @param commissionRate    정산 시점 요율 스냅샷(MVP는 상수 고정값)
 * @param commissionAmount  (grossAmount - refundAmount) x commissionRate
 * @param netAmount         업체 지급액 = (grossAmount - refundAmount) - commissionAmount
 * @param status            PENDING(계산됨) / CONFIRMED(확정) / PAID(2차 범위)
 */
public record SettlementResponse(
        Long settlementId,
        Long fairId,
        Long businessId,
        Long grossAmount,
        Long refundAmount,
        BigDecimal commissionRate,
        Long commissionAmount,
        Long netAmount,
        String status,
        LocalDateTime confirmedAt,
        Long confirmedByUserId
) {

    public static SettlementResponse from(SettlementRow row) {
        return new SettlementResponse(
                row.getSettlementId(),
                row.getFairId(),
                row.getBusinessId(),
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
