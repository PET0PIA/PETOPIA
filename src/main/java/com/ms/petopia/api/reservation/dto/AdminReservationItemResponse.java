package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record AdminReservationItemResponse(
        Long reservationId,
        String reservationNo,
        String reserverName,
        LocalDate visitDate,
        LocalTime entryStartTime,
        LocalTime entryEndTime,
        String reservationStatus,
        long amount,
        LocalDateTime reservedAt
) {
    public static AdminReservationItemResponse from(AdminReservationRow row) {
        return new AdminReservationItemResponse(
                row.getReservationId(),
                row.getReservationNo(),
                row.getReserverName(),
                row.getVisitDate(),
                row.getEntryStartTime(),
                row.getEntryEndTime(),
                row.getReservationStatus(),
                row.getAmount(),
                row.getReservedAt()
        );
    }
}
