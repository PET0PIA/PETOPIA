package com.ms.petopia.api.refund.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 환불 요청 수신 API의 요청 바디.
 *
 * <p>refundReason/requestedByDomain을 문자열이 아니라 enum으로 받아서 문서에 없는 값이
 * 감사 기록(REFUND 테이블)에 그대로 저장되는 걸 막는다 — 정의에 없는 값을 보내면 JSON
 * 역직렬화 단계에서 바로 400으로 거절된다(GlobalExceptionHandler가 처리).
 *
 * @param refundReason        환불 5경로 중 하나
 * @param requestedByDomain   환불을 촉발한 도메인. 감사·추적용.
 */
public record RefundRequest(
        @NotNull RefundReason refundReason,
        @NotNull RequestedByDomain requestedByDomain
) {
}
