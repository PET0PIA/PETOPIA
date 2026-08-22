package com.ms.petopia.api.reservation.dto;

import java.util.List;

/**
 * 유료 현장 직접예매에서만 취소·환불 불가 약관 동의가 필수다.
 *
 * @param petIds 함께 온 반려동물의 petId 목록. 사전예약과 같은 규칙을 따른다(P2·P3·P4·P5·P6).
 *               현장예매에도 동반을 받는 이유는 이 데이터가 방문 통계의 재료이기 때문이다 -
 *               현장에서 온 관람객만 빠지면 알레르기·품종 분포가 사전예약 쪽으로 치우친다.
 */
public record CreateOnsiteReservationRequest(
        Boolean reservationTermsAgreed,
        String reservationTermsVersion,
        List<Long> petIds
) {
    /** 반려동물 동반이 없던 시절 호출부 호환용. 동반 없음(빈 목록)으로 채운다. */
    public CreateOnsiteReservationRequest(Boolean reservationTermsAgreed, String reservationTermsVersion) {
        this(reservationTermsAgreed, reservationTermsVersion, List.of());
    }
}
