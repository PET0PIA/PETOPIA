package com.ms.petopia.api.settlement.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * SETTLEMENT_ITEM 테이블 한 행 — 정산 금액이 어떤 결제·환불 건들로 구성됐는지 남기는
 * 감사근거용 상세 내역. 정산 금액 자체는 SettlementRow에 이미 합계로 저장되므로,
 * 이 행들은 조회 API의 응답 필드가 아니라 내부 기록용이다.
 */
@Getter
@Setter
@ToString
public class SettlementItemRow {
    private Long settlementItemId;
    private Long settlementId;
    private Long paymentId;
    private Long refundId;
    private Long amountIncluded;
}
