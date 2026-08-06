package com.ms.petopia.api.settlement.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SETTLEMENT 테이블 한 행을 그대로 담는 매퍼 결과용 클래스. */
@Getter
@Setter
@ToString
public class SettlementRow {
    private Long settlementId;
    private Long fairId;
    private Long businessId;
    private Long grossAmount;
    private Long refundAmount;
    private BigDecimal commissionRate;
    private Long commissionAmount;
    private Long netAmount;
    private String status;
    private LocalDateTime paidAt;
    private LocalDateTime confirmedAt;
    private Long confirmedByUserId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
