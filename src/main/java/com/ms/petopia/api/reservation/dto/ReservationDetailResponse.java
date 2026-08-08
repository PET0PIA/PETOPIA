package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 예약 단건 상세. 목록 아이템 필드에 더해 예약 유형과 케밥 메뉴 노출용 가능 여부 플래그를 담는다.
 * canChangeVisitDate·canCancel은 상태·유형 기준의 대략적 판단이며, 실제 마감 시각 검증은
 * 방문일 변경·취소 API가 수행한다(기한 초과 시 R018·R019).
 */
public record ReservationDetailResponse(
        Long reservationId,
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
        boolean canCancel
) {
}
