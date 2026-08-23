package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 예매 화면 날짜 카드 한 장.
 *
 * @param available            잔여석 기준 예매 가능 여부. 내가 이미 예약한 날인지는 보지 않는다 -
 *                             "남은 자리가 있는가"와 "내가 또 살 수 있는가"는 다른 판단이라
 *                             화면이 각각 다른 문구("마감" / "이미 예약함")로 안내해야 한다
 * @param myReservationId      로그인 사용자가 이 날짜에 이미 잡아둔 예약의 ID. 없거나 비로그인이면 null
 * @param myReservationStatus  그 예약의 상태(PENDING_PAYMENT/CONFIRMED/CHECKED_IN). 없으면 null
 */
public record ReservationAvailabilityDateResponse(
        LocalDate visitDate,
        LocalTime entryStartTime,
        LocalTime entryEndTime,
        long remainingCapacity,
        boolean available,
        Long myReservationId,
        String myReservationStatus
) {
}
