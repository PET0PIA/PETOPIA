package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;

/**
 * 사전예약 기간 수정 요청. 둘 다 필수다(부분 수정 아님) - 신청서 수정(PATCH)과 달리 이 화면은
 * 필드가 두 개뿐이라 "생략하면 유지" 구분을 둘 이유가 없다.
 */
public record UpdateReservationPeriodRequest(
        LocalDate reservationStartDate,
        LocalDate reservationEndDate
) {
}
