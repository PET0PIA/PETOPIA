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
    /**
     * 아래 2개는 fairs/businesses를 LEFT JOIN해서 채운다(selectByIdWithRefund/selectByFilter
     * 전용 - refund 6개와 동일한 이유로 내부 비즈니스 로직이 쓰는 조회엔 안 붙인다). businessName은
     * 관람객 예약금 결제처럼 businessId가 없으면 null.
     */
    private String fairName;
    /** 행사 신청 시 입력한 담당자 정보(fairs 테이블). manager_phone만 원래 nullable이라 null일 수 있다. */
    private String fairManagerName;
    private String fairManagerPhone;
    private String fairManagerEmail;
    private String businessName;
    /**
     * 아래 3개는 application_form을 LEFT JOIN해서 채운다 - VENDOR_FEE(참가업체 참가비) 결제만
     * applicationId가 있어서 그 외 결제유형은 전부 null. 부스(참가) 신청서 작성 시 입력한
     * 담당자 정보다.
     */
    private String applicationManagerName;
    private String applicationManagerPhone;
    private String applicationManagerEmail;
    private Long payerUserId;
    /**
     * users를 payerUserId로 LEFT JOIN해서 채운다(selectByIdWithRefund/selectByFilter 전용,
     * applicationManagerName과 동일한 이유). payerUserId는 결제유형(RESERVATION_DEPOSIT/
     * VENDOR_FEE/FAIR_OPENING_FEE) 상관없이 다 채워지므로 이 값도 다 채워진다 - "예약티켓예매결제현황"
     * 화면이 예약자명으로 쓰려고 2026-08-24에 추가했다.
     */
    private String payerNickname;
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

    /**
     * 아래 6개는 REFUND 테이블을 LEFT JOIN해서 채운다(selectByIdWithRefund/selectByFilter 전용 -
     * selectById/selectByIdForUpdate 등 내부 비즈니스 로직이 쓰는 조회는 그대로 둬서 불필요한
     * JOIN 비용을 안 지운다). 이 결제에 걸린 환불이 없으면 전부 null. UK_REFUND_PAYMENT
     * 덕분에 결제 1건당 환불은 최대 1건이라 컬럼으로 바로 붙여도 행이 늘어나지 않는다.
     */
    private Long refundId;
    private String refundStatus;
    private Long refundAmount;
    private String refundReason;
    private String refundRequestedByDomain;
    private LocalDateTime refundRequestedAt;
    private LocalDateTime refundProcessedAt;
}
