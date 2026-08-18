package com.ms.petopia.api.fair.dto;

import java.time.LocalDateTime;

/**
 * 정산/결제 도메인이 정산 계산·확정 직전에 "이 행사 취소됐는지" 확인하는 내부 계약 API의
 * 응답이다. reservation 도메인의 {@code ReservationPaymentContextResponse}와 같은 역할 -
 * 판단 로직은 이 도메인이 갖고, 호출부는 결과만 그대로 받아 쓴다.
 *
 * @param fairId     대상 행사 PK
 * @param canceled   취소 여부({@code fairs.canceled_at IS NOT NULL})
 * @param canceledAt 취소 승인 일시. 취소 아니면 null
 */
public record FairCancellationStatusResponse(
        Long fairId,
        boolean canceled,
        LocalDateTime canceledAt
) {
}
