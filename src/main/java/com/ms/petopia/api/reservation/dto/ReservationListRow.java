package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
public class ReservationListRow {
    private Long reservationId;
    private String fairName;
    private String fairPosterImageUrl;
    private LocalDate visitDate;
    private LocalTime entryStartTime;
    private LocalTime entryEndTime;
    private String reservationStatus;
    private long amount;
    private LocalDateTime reservedAt;
    private LocalDateTime checkedInAt;
}
