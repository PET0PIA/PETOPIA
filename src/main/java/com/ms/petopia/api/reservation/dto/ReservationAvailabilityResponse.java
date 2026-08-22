package com.ms.petopia.api.reservation.dto;

import java.util.List;

/**
 * 예약 화면용 날짜별 예약 가능 정보.
 *
 * @param petAllowed 반려동물 동반 가능 여부. 예약 화면이 반려동물 선택 UI를 띄울지 판단한다 -
 *                   행사 요약 API를 한 번 더 호출하지 않도록 여기에 함께 담는다.
 */
public record ReservationAvailabilityResponse(
        Long fairId,
        long reservationFee,
        boolean petAllowed,
        List<ReservationAvailabilityDateResponse> dates
) {
}
