package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReservationPaymentContextRow {
    private Long reservationId;
    private Long fairId;
    private Long userId;
    private String reservationType;
    private String status;
    private long reservationAmount;
    private LocalDateTime paymentExpiresAt;
}
