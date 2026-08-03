package com.ms.petopia.api.reservation.dto;

/** 유료 현장 직접예매에서만 취소·환불 불가 약관 동의가 필수다. */
public record CreateOnsiteReservationRequest(
        Boolean reservationTermsAgreed,
        String reservationTermsVersion
) {
}
