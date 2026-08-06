package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;

@Getter
@Setter
public class ReservationCancellationContext {
    private Long reservationId;
    private Long fairId; // 실시간 통계 확인용
    private Long userId;
    private LocalDate visitDate;
    private String reservationType;
    private String status;
    private long reservationAmount;
    private Integer cancelDeadlineHours;
    private LocalTime entryStartTime;
}
