package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 결제 도메인이 개설비 결제 생성 직전에 호출하는 내부 계약. reservation 도메인의
 * {@code ReservationPaymentContextResponse}와 같은 역할이다 - 클라이언트가 보낸 금액을
 * 신뢰하지 않고, 승인 시 확정해 fairs에 저장해 둔 금액을 그대로 내려준다.
 *
 * <p>결제자가 이 행사의 담당자인지 검증하는 필드(payerUserId 등)는 아직 없다 - 지금은
 * 소유자 검증 없이 인증된 사용자면 누구나 개설비를 결제할 수 있다(추후 별도 작업으로 보강 예정).
 */
public record FairOpeningFeePaymentContextResponse(
        Long fairId,
        long amount,
        LocalDateTime paymentDueAt
) {
}
