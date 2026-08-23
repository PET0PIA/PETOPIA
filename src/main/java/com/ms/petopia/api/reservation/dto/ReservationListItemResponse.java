package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 내 예약 목록 한 건.
 *
 * <p>{@code refundStatus}는 예약금 환불 상태(REQUESTED/COMPLETED/REJECTED)이고 환불이 없으면
 * null이다. 목록 카드는 "취소됨" 배지만으로는 환불받은 취소·결제 전 취소·무료 예약 취소를
 * 구분할 수 없어서, 이 값으로 금액줄 표시를 가른다. 환불 금액은 목록에 다시 쓰지 않기로 해서
 * 싣지 않는다.
 */
public record ReservationListItemResponse(
        Long reservationId,
        String fairName,
        String fairPosterImageUrl,
        LocalDate visitDate,
        LocalTime entryStartTime,
        LocalTime entryEndTime,
        String reservationStatus,
        boolean isEnded,
        boolean qrAvailable,
        boolean paymentAvailable,
        /**
         * 결제 대기 예약의 결제 제한시각. 화면이 남은 시간을 카운트다운으로 보여준다 -
         * "결제 대기"라는 배지만으로는 언제까지 결제해야 하는지 알 수 없었다.
         * 결제 대기가 아니거나 무료 예약이면 null.
         */
        LocalDateTime paymentExpiresAt,
        long amount,
        LocalDateTime reservedAt,
        LocalDateTime checkedInAt,
        String refundStatus,
        /** 주최측 행사 취소로 자동 취소된 예약인지. 사용자가 직접 취소한 건과 구분해 안내한다. */
        boolean canceledByFairCancellation
) {
}
