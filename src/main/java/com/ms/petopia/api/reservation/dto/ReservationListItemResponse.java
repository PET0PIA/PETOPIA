package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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
        /** 주최측 행사 취소로 자동 취소된 예약인지. 사용자가 직접 취소한 건과 구분해 안내한다. */
        boolean canceledByFairCancellation
) {
}
