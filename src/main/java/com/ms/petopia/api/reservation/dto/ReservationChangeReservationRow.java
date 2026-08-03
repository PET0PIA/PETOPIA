package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ReservationChangeReservationRow {
    private Long reservationId;
    private Long fairId;
    private Long userId;
    private LocalDate visitDate;
    private String status;
}
