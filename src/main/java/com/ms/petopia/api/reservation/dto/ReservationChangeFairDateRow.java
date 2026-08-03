package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class ReservationChangeFairDateRow {
    private Long fairDateId;
    private LocalDate operationDate;
    private int capacity;
    private LocalTime entryStartTime;
    private LocalTime entryEndTime;
}
