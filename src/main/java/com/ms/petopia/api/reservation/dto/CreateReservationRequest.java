package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 예약 생성 요청.
 *
 * <p>예약 매수는 1매로 고정하고 금액은 서버에서 계산하므로 요청으로 받지 않는다.
 * 약관 동의 필드는 예약금이 있는 행사에서만 필수다.
 *
 * @param petIds 함께 갈 반려동물의 petId 목록. 비어 있으면 동반 없이 예약한다(정책 P3).
 *               반려동물은 인원이 아니므로 매수·정원·QR 수에 영향을 주지 않는다(P6).
 *               동반 금지 행사에 값을 담아 보내면 R024로 거절한다(P2).
 */
public record CreateReservationRequest(
        LocalDate visitDate,
        Boolean reservationTermsAgreed,
        String reservationTermsVersion,
        List<Long> petIds
) {
    /** 반려동물 동반이 없던 시절 호출부 호환용. 동반 없음(빈 목록)으로 채운다. */
    public CreateReservationRequest(LocalDate visitDate, Boolean reservationTermsAgreed, String reservationTermsVersion) {
        this(visitDate, reservationTermsAgreed, reservationTermsVersion, List.of());
    }
}
