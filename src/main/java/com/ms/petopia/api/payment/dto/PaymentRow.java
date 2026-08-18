package com.ms.petopia.api.payment.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.ToString.Exclude;

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

    //멱득성-같은요청을 여러번 보내도 결과가 딱 한 번 처리한 것과 같아야하는
    // 그걸 구현하는 실제컬럼
    private String idempotencyKey;
    /** 토스 승인용 주문번호. 결제 시도마다 새로 발급해 저장한다. */
    private String orderId;
    private String tossPaymentKey;
    private LocalDateTime updatedAt;

    /** 간편결제(네이버페이 등) 제공사. 일반 카드/계좌이체 등이면 null. */
    private String easyPayProvider;
    /** 아래 4개는 가상계좌로 결제했을 때만 값이 있다(WAITING_FOR_DEPOSIT 상태로 전이할 때 채움). */
    private String virtualAccountBankCode;
    private String virtualAccountNumber;
    private LocalDateTime virtualAccountDueDate;
    /** 입금 웹훅 검증용 secret. 절대 API 응답으로 노출하면 안 돼서 toString에서도 제외한다. */
    @Exclude
    private String virtualAccountSecret;
}
