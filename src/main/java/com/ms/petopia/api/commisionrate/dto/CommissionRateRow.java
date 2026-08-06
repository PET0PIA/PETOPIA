package com.ms.petopia.api.commisionrate.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** COMMISSION_RATE 테이블 한 행을 그대로 담는 매퍼 결과용 클래스. */
@Getter
@Setter
@ToString
public class CommissionRateRow {

    private Long commissionRateId;
    private String scope;
    private BigDecimal rate;
    private Long fairId;
    private Long updatedByUserId;
    private LocalDateTime updatedAt;
}
