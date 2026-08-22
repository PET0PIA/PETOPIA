package com.ms.petopia.api.settlement.dto;

import java.math.BigDecimal;

/**
 * 행사별 매출 요약 응답(SUPER_ADMIN 정산·수수료 화면 전용).
 *
 * <p>기존 {@link SettlementResponse}(정산 1건 = 행사·업체 조합, 참가비만, PENDING/CONFIRMED
 * 워크플로우 있음)와는 완전히 별개다. 이건 행사 하나를 통째로 - 티켓예매(예약금) + 참가비를
 * 합친 - 매출로 보고, DB에 저장하지 않고 매번 결제/환불 데이터를 그대로 다시 집계해서 보여주는
 * 조회 전용 화면이다.
 *
 * @param ticketAmount    완료된 예약금 결제 합계 - 그 환불 합계
 * @param vendorFeeAmount 완료된 참가비 결제 합계 - 그 환불 합계
 * @param grossAmount     ticketAmount + vendorFeeAmount
 * @param commissionRate  이 행사에 지금 적용되는 요율(행사별 override 있으면 그것, 없으면 전역
 *                        기본값) - CommissionRateService.resolveEffectiveRate와 동일 기준.
 *                        저장된 스냅샷이 아니라 조회 시점 값이라 요율이 바뀌면 이 값도 바뀐다.
 * @param platformAmount  grossAmount x commissionRate (반올림) - 플랫폼(SUPER_ADMIN) 몫
 * @param businessAmount  grossAmount - platformAmount - 행사관리자(EVENT_ADMIN) 몫
 */
public record FairRevenueSummaryResponse(
        Long fairId,
        String fairName,
        Long ticketAmount,
        Long vendorFeeAmount,
        Long grossAmount,
        BigDecimal commissionRate,
        Long platformAmount,
        Long businessAmount
) {
}
