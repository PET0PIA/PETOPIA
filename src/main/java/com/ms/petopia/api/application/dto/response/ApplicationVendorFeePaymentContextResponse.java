package com.ms.petopia.api.application.dto.response;

import java.time.LocalDateTime;

/**
 * 결제 도메인이 참가비 결제 생성 직전에 호출하는 내부 계약. fair 도메인의
 * FairOpeningFeePaymentContextResponse와 같은 역할이다 - 클라이언트가 보낸 금액을 신뢰하지 않고,
 * 승인 시 확정해 application에 저장해 둔 finalPrice를 그대로 내려준다.
 *
 * <p>예약금과 동일하게 신청 당사자(사업자 소유주)가 스스로 결제하는 구조라 payerUserId를 포함한다
 * - 결제 도메인이 이 값으로 본인 확인을 한다({@code PaymentService#payVendorFee} 참고).
 */
public record ApplicationVendorFeePaymentContextResponse(
        Long applicationId,
        Long fairId,
        Long businessId,
        Long payerUserId,
        long amount,
        LocalDateTime paymentDueAt
) {
}
