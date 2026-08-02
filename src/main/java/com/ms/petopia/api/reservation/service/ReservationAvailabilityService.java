package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationAvailabilityDateResponse;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityDateRow;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityFair;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityResponse;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationAvailabilityService {

    private final ReservationMapper reservationMapper;
    private final ReservationTimeProvider timeProvider;

    /** 예약 화면에 표시할 행사별 예약금과 날짜별 잔여 사전예약 정원을 반환한다. */
    @Transactional(readOnly = true)
    public ReservationAvailabilityResponse getAvailability(Long fairId) {
        if (fairId == null || fairId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ReservationAvailabilityFair fair = reservationMapper.selectAvailabilityFair(fairId);
        if (fair == null) {
            throw new CommonException(ErrorCode.RESERVATION_FAIR_NOT_FOUND);
        }

        LocalDate today = timeProvider.today();
        validateReservableFair(fair, today);

        List<ReservationAvailabilityDateResponse> dates = reservationMapper
                .selectAvailabilityDates(fairId, today)
                .stream()
                .map(this::toResponse)
                .toList();

        return new ReservationAvailabilityResponse(fairId, fair.getReservationFee(), dates);
    }

    private void validateReservableFair(ReservationAvailabilityFair fair, LocalDate today) {
        if (fair.getPublishedAt() == null
                || fair.getCanceledAt() != null
                || fair.getReservationStartDate() == null
                || fair.getReservationEndDate() == null
                || today.isBefore(fair.getReservationStartDate())
                || today.isAfter(fair.getReservationEndDate())) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_OPEN);
        }
        if (fair.getReservationFee() < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ReservationAvailabilityDateResponse toResponse(ReservationAvailabilityDateRow row) {
        long remainingCapacity = Math.max((long) row.getCapacity() - row.getOccupiedCount(), 0);
        return new ReservationAvailabilityDateResponse(
                row.getVisitDate(),
                row.getEntryStartTime(),
                row.getEntryEndTime(),
                remainingCapacity,
                remainingCapacity > 0
        );
    }
}
