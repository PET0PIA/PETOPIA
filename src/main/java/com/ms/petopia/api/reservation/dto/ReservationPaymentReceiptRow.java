package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class ReservationPaymentReceiptRow {
    private Long confirmationId;
    private String eventId;
    private Long paymentId;
    private Long reservationId;
    private long paidAmount;
    private LocalDateTime paidAt;
    private LocalDateTime receivedAt;
}
