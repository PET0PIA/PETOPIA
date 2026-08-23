package com.ms.petopia.api.reservation.dto;

/**
 * 관리자 대행 예약취소 요청.
 *
 * <p>관람객 자진취소({@link CancelReservationRequest})와 달리 사유가 <b>필수</b>다 — 남의 예약을
 * 지우는 동작이라 나중에 "왜 취소됐느냐"는 문의가 들어오면 이 값이 유일한 근거가 된다.
 * 예약 이력(reservation_histories.change_reason)과 감사 로그에 함께 남고, 관람객에게 가는
 * 취소 알림 본문에도 그대로 실린다.
 *
 * @param reason 취소 사유(필수, 500자 이하)
 */
public record AdminCancelReservationRequest(
        String reason
) {
}
