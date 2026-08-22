package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;

/**
 * 행사의 사전예약 기간 조회·수정 결과.
 *
 * @param fairId               행사 PK
 * @param reservationStartDate 사전예약 시작일. 아직 설정 안 됐으면 null
 * @param reservationEndDate   사전예약 종료일. 아직 설정 안 됐으면 null
 */
public record ReservationPeriodResponse(
        Long fairId,
        LocalDate reservationStartDate,
        LocalDate reservationEndDate
) {
}
