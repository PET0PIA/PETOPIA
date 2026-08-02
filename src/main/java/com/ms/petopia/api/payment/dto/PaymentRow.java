package com.ms.petopia.api.payment.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * PAYMENT 테이블 한 행을 그대로 담는 매퍼 결과용 클래스.
 *
 * <p>record가 아니라 세터가 있는 mutable 클래스인 이유: MyBatis가 resultType으로
 * 결과를 채울 때 기본 생성자 + 세터 방식을 쓰기 때문이다 (record의 불변 생성자 방식은
 * 컬럼명-파라미터명이 정확히 일치해야 해서 매퍼 결과 매핑에는 잘 안 맞는다).
 * 서비스 계층에서는 이 Row를 그대로 노출하지 않고 {@link PaymentResponse}로 변환해서 반환한다.
 */
@Getter
@Setter
@ToString
public class PaymentRow {
    private Long paymentId;
    private String paymentType;
    private Long amount;
    private String status;
    private String method;
    private LocalDateTime paidAt;
    private LocalDateTime createdAt;
    private Long fairId;
    private Long businessId;
    private Long payerUserId;
    private Long reservationId;
    private Long applicationId;
}
