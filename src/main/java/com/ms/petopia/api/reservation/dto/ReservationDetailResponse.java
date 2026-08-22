package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 예약 단건 상세. 목록 아이템 필드에 더해 예약 유형과 케밥 메뉴 노출용 가능 여부 플래그를 담는다.
 * canChangeVisitDate·canCancel은 상태·유형 기준의 대략적 판단이며, 실제 마감 시각 검증은
 * 방문일 변경·취소 API가 수행한다(기한 초과 시 R018·R019).
 *
 * @param paymentId     예약금 결제의 ID. 무료 예약(결제 행 없음)이면 null
 * @param paymentMethod 표시용 결제수단. 결제 완료 전이면 "결제 전", 무료 예약이면 null.
 *                      DB의 payment.status·method·easy_pay_provider를 조합한 완성 문구다 —
 *                      화면이 상태별 분기를 다시 들고 있지 않도록 서버에서 판단을 끝낸다
 *                      (ReservationQueryService.paymentMethodLabel 참고)
 */
public record ReservationDetailResponse(
        Long reservationId,
        String reservationNo,
        Long fairId,
        String fairName,
        String fairPosterImageUrl,
        LocalDate visitDate,
        LocalTime entryStartTime,
        LocalTime entryEndTime,
        String reservationStatus,
        String reservationType,
        boolean isEnded,
        boolean qrAvailable,
        boolean paymentAvailable,
        long amount,
        LocalDateTime reservedAt,
        LocalDateTime checkedInAt,
        boolean canChangeVisitDate,
        boolean canCancel,
        Long paymentId,
        String paymentMethod
) {
}
