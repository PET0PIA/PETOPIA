package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;

/**
 * 예약 생성 요청.
 *
 * <p>예약 매수는 1매로 고정하고 금액은 서버에서 계산하므로 요청으로 받지 않는다.
 * 약관 동의 필드는 예약금이 있는 행사에서만 필수다.
 */
public record CreateReservationRequest(
        LocalDate visitDate,
        Boolean reservationTermsAgreed,
        String reservationTermsVersion
) {
}
