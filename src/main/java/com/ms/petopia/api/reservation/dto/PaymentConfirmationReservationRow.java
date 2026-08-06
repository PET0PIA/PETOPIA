package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class PaymentConfirmationReservationRow {
    private Long reservationId;
    private Long fairId; // 실시간 통계 확인용
    private String status;
    private long reservationAmount;
    private LocalDateTime paymentExpiresAt;
}
