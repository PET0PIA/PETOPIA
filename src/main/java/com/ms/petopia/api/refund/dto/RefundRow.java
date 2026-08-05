package com.ms.petopia.api.refund.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * REFUND 테이블 한 행을 그대로 담는 매퍼 결과용 클래스.
 *
 * <p>{@link com.ms.petopia.api.payment.dto.PaymentRow}와 같은 이유로 record가 아니라
 * 세터가 있는 mutable 클래스로 만든다(MyBatis 기본 생성자+세터 매핑 방식).
 */
@Getter
@Setter
@ToString
public class RefundRow {
    private Long refundId;
    private Long paymentId;
    private String refundReason;
    private String requestedByDomain;
    private Long refundAmount;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
