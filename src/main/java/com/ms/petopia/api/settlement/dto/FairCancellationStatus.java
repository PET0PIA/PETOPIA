package com.ms.petopia.api.settlement.dto;

import java.time.LocalDateTime;

/**
 * 행사 도메인의 취소 상태 조회(GET .../fairs/{fairId}/cancellation-status) 응답을
 * 그대로 매핑. fair 도메인의 {@code FairCancellationStatusResponse}와 필드가 같다 -
 * 도메인 간 결합을 피하려고 그 클래스를 직접 참조하지 않고 정산 쪽에서 같은 모양으로
 * 따로 둔다({@code ReservationPaymentContext}가 예약 도메인 응답을 매핑하는 것과 동일한 패턴).
 *
 * @param fairId     대상 행사 PK
 * @param canceled   취소 여부
 * @param canceledAt 취소 승인 일시. 취소 아니면 null
 */
public record FairCancellationStatus(
        Long fairId,
        boolean canceled,
        LocalDateTime canceledAt
) {
}
