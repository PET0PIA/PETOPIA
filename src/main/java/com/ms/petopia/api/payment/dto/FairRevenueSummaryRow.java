package com.ms.petopia.api.payment.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 행사별 매출 요약(티켓예매 + 참가비) 조회용 매퍼 결과 행.
 *
 * <p>{@link PaymentRow}와 같은 이유로 mutable + 세터 방식을 쓴다(MyBatis resultType 매핑).
 * 수수료율·플랫폼몫·행사업체몫은 여기 없다 — 이 행은 순수 매출 집계일 뿐이고, 수수료 계산은
 * {@code SettlementService}가 {@code CommissionRateService}로 요율을 조회해 덧붙인다.
 */
@Getter
@Setter
public class FairRevenueSummaryRow {
    private Long fairId;
    private String fairName;
    /** 완료된 예약금(RESERVATION_DEPOSIT) 결제 합계 - 그 결제들의 완료된 환불 합계. */
    private Long ticketAmount;
    /** 완료된 참가비(VENDOR_FEE) 결제 합계 - 그 결제들의 완료된 환불 합계. */
    private Long vendorFeeAmount;
}
