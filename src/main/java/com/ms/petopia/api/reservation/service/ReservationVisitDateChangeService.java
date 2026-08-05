package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationChangeFairDateRow;
import com.ms.petopia.api.reservation.dto.ReservationChangeReservationRow;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateRequest;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateResponse;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import com.ms.petopia.api.reservation.mapper.ReservationChangeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationVisitDateChangeService {

    private static final String CONFIRMED = "CONFIRMED";
    private static final String ADVANCE = "ADVANCE";
    private static final long CHANGE_DEADLINE_HOURS = 12;

    private final ReservationChangeMapper changeMapper;
    private final EntryMapper entryMapper;
    private final ReservationTimeProvider timeProvider;

    /** 확정된 사전예약의 방문 날짜와 발급된 QR 유효시간을 함께 변경한다. */
    @Transactional
    public UpdateReservationVisitDateResponse changeVisitDate(
            Long reservationId,
            Long userId,
            UpdateReservationVisitDateRequest request
    ) {
        validateRequest(reservationId, userId, request);

        ReservationChangeReservationRow reservation = changeMapper.selectReservationForUpdate(reservationId);
        if (reservation == null) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (!userId.equals(reservation.getUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
        if (!ADVANCE.equals(reservation.getReservationType())
                || !CONFIRMED.equals(reservation.getStatus())) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }

        List<ReservationChangeFairDateRow> fairDates = changeMapper.selectFairDatesForUpdate(
                reservation.getFairId(),
                List.of(reservation.getVisitDate(), request.visitDate())
        );
        ReservationChangeFairDateRow currentDate = findDate(fairDates, reservation.getVisitDate());
        ReservationChangeFairDateRow targetDate = findDate(fairDates, request.visitDate());
        if (currentDate == null || targetDate == null) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }

        LocalDateTime now = timeProvider.now();
        LocalDateTime deadline = LocalDateTime.of(
                currentDate.getOperationDate(),
                currentDate.getEntryStartTime()
        ).minusHours(CHANGE_DEADLINE_HOURS);
        if (now.isAfter(deadline)) {
            throw new CommonException(ErrorCode.RESERVATION_CHANGE_DEADLINE_EXCEEDED);
        }

        if (request.visitDate().equals(reservation.getVisitDate())) {
            return response(reservationId, reservation.getVisitDate(), targetDate, reservation.getStatus());
        }
        if (!targetDate.getOperationDate().isAfter(timeProvider.today())) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }
        if (targetDate.getEntryStartTime() == null || targetDate.getEntryEndTime() == null
                || targetDate.getEntryEndTime().isBefore(targetDate.getEntryStartTime())) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }
        int occupied = changeMapper.countCapacityOccupyingAdvanceReservations(
                reservation.getFairId(),
                request.visitDate()
        );
        if (occupied >= targetDate.getCapacity()) {
            throw new CommonException(ErrorCode.RESERVATION_SOLD_OUT);
        }

        int updated = changeMapper.updateVisitDate(reservationId, request.visitDate(), now);
        if (updated != 1) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        entryMapper.updateEntryQrAvailability(
                reservationId,
                LocalDateTime.of(targetDate.getOperationDate(), targetDate.getEntryStartTime()),
                LocalDateTime.of(targetDate.getOperationDate(), targetDate.getEntryEndTime()),
                now
        );
        changeMapper.insertVisitDateChangedHistory(
                reservationId,
                reservation.getVisitDate(),
                request.visitDate(),
                userId,
                now
        );

        return response(reservationId, reservation.getVisitDate(), targetDate, reservation.getStatus());
    }

    private void validateRequest(
            Long reservationId,
            Long userId,
            UpdateReservationVisitDateRequest request
    ) {
        if (reservationId == null || reservationId <= 0
                || userId == null || userId <= 0
                || request == null || request.visitDate() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ReservationChangeFairDateRow findDate(
            List<ReservationChangeFairDateRow> fairDates,
            LocalDate operationDate
    ) {
        return fairDates.stream()
                .filter(fairDate -> operationDate.equals(fairDate.getOperationDate()))
                .findFirst()
                .orElse(null);
    }

    private UpdateReservationVisitDateResponse response(
            Long reservationId,
            LocalDate previousVisitDate,
            ReservationChangeFairDateRow targetDate,
            String status
    ) {
        return new UpdateReservationVisitDateResponse(
                reservationId,
                previousVisitDate,
                targetDate.getOperationDate(),
                targetDate.getEntryStartTime(),
                targetDate.getEntryEndTime(),
                status
        );
    }
}
