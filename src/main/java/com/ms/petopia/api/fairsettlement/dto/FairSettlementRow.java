package com.ms.petopia.api.fairsettlement.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** FAIR_SETTLEMENT 테이블 한 행을 그대로 담는 매퍼 결과용 클래스. 행사 하나당 최대 1행(UK_FAIR_SETTLEMENT_FAIR). */
@Getter
@Setter
@ToString
public class FairSettlementRow {
    private Long fairSettlementId;
    private Long fairId;
    private Long grossAmount;
    private Long refundAmount;
    private BigDecimal commissionRate;
    private Long commissionAmount;
    private Long netAmount;
    private String status;
    /** PENDING 정산에 포함된 결제가 환불돼서 재계산이 필요한 상태. confirm()이 이 값이면 거부한다(settlement 도메인과 동일 패턴). */
    private boolean needsRecalculation;
    private LocalDateTime paidAt;
    private LocalDateTime confirmedAt;
    private Long confirmedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
