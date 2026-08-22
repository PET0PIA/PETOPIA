package com.ms.petopia.api.fairsettlement.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * FAIR_SETTLEMENT_ITEM 테이블 한 행 — 행사 정산 금액이 어떤 결제·환불 건들(그 행사의 모든
 * 참가업체 결제 포함)로 구성됐는지 남기는 감사근거용 상세 내역. 기존 settlement 도메인의
 * SettlementItemRow와 동일한 역할이지만 완전히 별개 테이블을 쓴다.
 */
@Getter
@Setter
@ToString
public class FairSettlementItemRow {
    private Long fairSettlementItemId;
    private Long fairSettlementId;
    private Long paymentId;
    private Long refundId;
    private Long amountIncluded;
}
