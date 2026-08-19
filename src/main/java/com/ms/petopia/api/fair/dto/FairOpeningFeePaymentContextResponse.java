package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 결제 도메인이 개설비 결제 생성 직전에 호출하는 내부 계약. reservation 도메인의
 * {@code ReservationPaymentContextResponse}와 같은 역할이다 - 클라이언트가 보낸 금액을
 * 신뢰하지 않고, 승인 시 확정해 fairs에 저장해 둔 금액을 그대로 내려준다.
 *
 * <p>예약금·참가비와 달리 신청 당사자가 스스로 내는 구조가 아니라 SUPER_ADMIN이 대행
 * 결제하는 구조라서(2026-08-19 결정), 이 계약에는 payerUserId 같은 소유자 검증 필드가
 * 없다 - 결제 도메인이 role만으로 검증한다({@code PaymentService#payFairOpeningFee} 참고).
 */
public record FairOpeningFeePaymentContextResponse(
        Long fairId,
        long amount,
        LocalDateTime paymentDueAt
) {
}
