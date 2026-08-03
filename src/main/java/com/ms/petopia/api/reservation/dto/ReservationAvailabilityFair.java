package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ReservationAvailabilityFair {
    private Long fairId;
    private long reservationFee;
    private LocalDate reservationStartDate;
    private LocalDate reservationEndDate;
    private LocalDateTime publishedAt;
    private LocalDateTime canceledAt;
}
