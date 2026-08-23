package com.ms.petopia.api.payment.dto;

import java.time.LocalDateTime;

/**
 * 결제 단건 조회 응답.
 *
 * <p>{@link PaymentRow}(DB 로우 그대로)를 API 응답으로 바로 내보내지 않고 이 record로
 * 한 번 감싸는 이유: Row는 세터가 열려 있는 mutable 객체라 응답 계약(스펙)으로 쓰기엔
 * 안전하지 않고, 나중에 응답 전용 필드(예: 표시용 라벨)를 추가할 때도 DB 매핑과 분리돼 있어야
 * Row를 건드리지 않고 확장할 수 있다.
 *
 * @param paymentId    결제 PK
 * @param paymentType  결제 유형 (RESERVATION_DEPOSIT / VENDOR_FEE / FAIR_OPENING_FEE)
 * @param amount       결제 금액 (원화 정수단위)
 * @param status       결제 상태
 * @param method       결제 수단 (MVP 기준 "MOCK" 고정)
 * @param paidAt       결제 완료 시각 (미결제 상태면 null일 수 있음)
 * @param createdAt    결제 요청 생성 시각
 * @param fairId       행사 ID
 * @param businessId   참가업체(사업자) ID, 관람객 예약금 결제일 땐 null
 * @param payerUserId  결제자 USER ID
 * @param reservationId 예약 ID (예약금 결제일 때만 값 있음)
 * @param applicationId 참가신청 ID (참가비 결제일 때만 값 있음)
 * @param easyPayProvider 간편결제 제공사(네이버페이 등). 일반 카드/계좌이체 등이면 null
 * @param virtualAccountBankCode 가상계좌 은행 코드. 가상계좌 결제가 아니면 null
 * @param virtualAccountNumber   가상계좌 계좌번호. 가상계좌 결제가 아니면 null
 * @param virtualAccountDueDate  가상계좌 입금기한. 가상계좌 결제가 아니면 null
 *                               (웹훅 검증용 secret은 절대 응답에 포함하지 않는다)
 * @param refundId               이 결제에 걸린 환불 PK. 환불이 없으면 null(이하 refund* 전부 동일)
 * @param refundStatus           환불 상태 (REQUESTED / COMPLETED / REJECTED)
 * @param refundAmount           환불 금액
 * @param refundReason           환불 사유
 * @param refundRequestedByDomain 환불을 촉발한 도메인
 * @param refundRequestedAt      환불 요청 시각
 * @param refundProcessedAt      환불 처리 완료 시각. 아직 처리 전(REQUESTED)이면 null
 * @param fairName     행사 이름 (2026-08-23 추가 - 기존 필드 사이에 안 끼우고 끝에 붙였다,
 *                     테스트가 이 record를 위치 기반 인자로 직접 생성하는 곳이 많아서다)
 * @param businessName 참가업체(사업자) 이름, 관람객 예약금 결제일 땐 null
 * @param fairManagerName  행사 담당자 이름 (행사 등록 신청 시 입력)
 * @param fairManagerPhone 행사 담당자 연락처. 원래 선택 입력이라 null일 수 있음
 * @param fairManagerEmail 행사 담당자 이메일
 * @param applicationManagerName  부스(참가) 신청 담당자 이름. VENDOR_FEE 결제가 아니면 null
 * @param applicationManagerPhone 부스(참가) 신청 담당자 연락처. VENDOR_FEE 결제가 아니면 null
 * @param applicationManagerEmail 부스(참가) 신청 담당자 이메일. VENDOR_FEE 결제가 아니면 null
 */
public record PaymentResponse(
        Long paymentId,
        String orderId,
        String paymentType,
        Long amount,
        String status,
        String method,
        LocalDateTime paidAt,
        LocalDateTime createdAt,
        Long fairId,
        Long businessId,
        Long payerUserId,
        Long reservationId,
        Long applicationId,
        String easyPayProvider,
        String virtualAccountBankCode,
        String virtualAccountNumber,
        LocalDateTime virtualAccountDueDate,
        Long refundId,
        String refundStatus,
        Long refundAmount,
        String refundReason,
        String refundRequestedByDomain,
        LocalDateTime refundRequestedAt,
        LocalDateTime refundProcessedAt,
        String fairName,
        String businessName,
        String fairManagerName,
        String fairManagerPhone,
        String fairManagerEmail,
        String applicationManagerName,
        String applicationManagerPhone,
        String applicationManagerEmail
) {

    /**
     * {@link PaymentRow}를 응답 DTO로 변환한다.
     * 변환 로직을 record 안에 둬서, 서비스 코드에서는 매번 필드를 나열하지 않고 한 줄로 쓸 수 있게 한다.
     */
    public static PaymentResponse from(PaymentRow row) {
        return new PaymentResponse(
                row.getPaymentId(),
                row.getOrderId(),
                row.getPaymentType(),
                row.getAmount(),
                row.getStatus(),
                row.getMethod(),
                row.getPaidAt(),
                row.getCreatedAt(),
                row.getFairId(),
                row.getBusinessId(),
                row.getPayerUserId(),
                row.getReservationId(),
                row.getApplicationId(),
                row.getEasyPayProvider(),
                row.getVirtualAccountBankCode(),
                row.getVirtualAccountNumber(),
                row.getVirtualAccountDueDate(),
                row.getRefundId(),
                row.getRefundStatus(),
                row.getRefundAmount(),
                row.getRefundReason(),
                row.getRefundRequestedByDomain(),
                row.getRefundRequestedAt(),
                row.getRefundProcessedAt(),
                row.getFairName(),
                row.getBusinessName(),
                row.getFairManagerName(),
                row.getFairManagerPhone(),
                row.getFairManagerEmail(),
                row.getApplicationManagerName(),
                row.getApplicationManagerPhone(),
                row.getApplicationManagerEmail()
        );
    }
}
