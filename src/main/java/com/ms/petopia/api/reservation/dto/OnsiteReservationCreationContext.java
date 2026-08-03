package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Getter
@Setter
@ToString
public class OnsiteReservationCreationContext {
    private Long fairId;
    private String fairName;
    private String fairStatus;
    private LocalDateTime publishedAt;
    private LocalDateTime canceledAt;
    private Long fairDateId;
    private LocalDate operationDate;
    private LocalTime entryStartTime;
    private LocalTime entryEndTime;
    private Long onsitePrice;
    private String onsiteSalesStatus;
}
