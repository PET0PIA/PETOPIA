package com.ms.petopia.api.fair.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * fair_cancel_refund_targets 테이블 한 행. "이 결제, 환불 처리해야 함"이라는 작업 하나를
 * 표현한다({@code FairCancelRefundJob} 참고). record가 아니라 세터가 있는 mutable
 * 클래스인 이유는 {@code PaymentRow}와 동일 - MyBatis resultType 매핑 방식 때문이다.
 */
@Getter
@Setter
public class FairCancelRefundTarget {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    private Long fairCancelRefundTargetId;
    private Long fairId;
    private Long paymentId;
    private String paymentType;
    private String status;
    private int attemptCount;
    private String lastError;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
