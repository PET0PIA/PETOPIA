package com.ms.petopia.api.payment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 행사개설비 결제 생성 요청.
 *
 * <p>참가비 결제와 마찬가지로 결제 도메인이 fair 테이블을 직접 조회하지 않기 때문에(애그리거트
 * 간 ID 참조 원칙 유지), 결제에 필요한 금액을 호출자가 직접 실어보낸다. fairId는 경로변수로
 * 받으므로 요청 바디엔 없다. 서버가 이 금액을 검증하지 않는 건 {@code VendorFeePaymentRequest}와
 * 동일한 MVP 트레이드오프다 — 다만 0원/음수 결제 같은 명백히 잘못된 값은 {@link Positive}로
 * 최소한 걸러낸다(CodeRabbit 리뷰 지적, PR #62).
 */
public record OpeningFeePaymentRequest(
        @NotNull @Positive Long amount
) {
}
