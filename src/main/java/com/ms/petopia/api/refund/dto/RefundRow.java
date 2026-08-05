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
    /**
     * REFUND 테이블 실제 컬럼이 아니다 — 조회 시(selectById/selectByPaymentId) payment와
     * JOIN해서 채워 넣는 편의 필드. 예약 도메인이 응답만 보고 바로 어떤 예약 건인지 알 수 있게
     * 하려고 추가함(VENDOR_FEE 결제의 환불이면 null).
     */
    private Long reservationId;
    private String refundReason;
    private String requestedByDomain;
    private Long refundAmount;
    private String status;
    private LocalDateTime requestedAt;
    private LocalDateTime processedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
