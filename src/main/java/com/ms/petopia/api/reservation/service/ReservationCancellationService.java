package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.CancelReservationRequest;
import com.ms.petopia.api.reservation.dto.CancelReservationResponse;
import com.ms.petopia.api.reservation.dto.ReservationCancellationContext;
import com.ms.petopia.api.reservation.mapper.ReservationCancellationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservationCancellationService {

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String ADVANCE = "ADVANCE";
    private static final String CANCELED = "CANCELED";
    private static final int DEFAULT_CANCEL_DEADLINE_HOURS = 12;

    private final ReservationCancellationMapper cancellationMapper;
    private final ReservationTimeProvider timeProvider;

    /** 결제 전 예약 또는 무료 사전예약을 취소한다. */
    @Transactional
    public CancelReservationResponse cancel(
            Long reservationId,
            Long userId,
            CancelReservationRequest request
    ) {
        validateRequest(reservationId, userId, request);

        ReservationCancellationContext reservation =
                cancellationMapper.selectCancellationContextForUpdate(reservationId);
        if (reservation == null) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (!userId.equals(reservation.getUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }

        LocalDateTime now = timeProvider.now();
        validateCancelable(reservation, now);

        String reason = normalizeReason(request == null ? null : request.reason());
        int updated = cancellationMapper.cancelReservation(
                reservationId,
                reservation.getStatus(),
                reason,
                userId,
                now
        );
        if (updated != 1) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        cancellationMapper.insertCanceledHistory(
                reservationId,
                reservation.getStatus(),
                reason,
                userId,
                now
        );

        return new CancelReservationResponse(reservationId, CANCELED, now);
    }

    private void validateRequest(Long reservationId, Long userId, CancelReservationRequest request) {
        if (reservationId == null || reservationId <= 0 || userId == null || userId <= 0
                || (request != null && request.reason() != null && request.reason().length() > 500)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateCancelable(ReservationCancellationContext reservation, LocalDateTime now) {
        if (PENDING_PAYMENT.equals(reservation.getStatus())) {
            return;
        }
        if (!CONFIRMED.equals(reservation.getStatus()) || !ADVANCE.equals(reservation.getReservationType())) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        if (reservation.getReservationAmount() > 0) {
            // TODO 결제 도메인의 환불 가능 여부 확인 및 환불 성공 통지 후 CANCELED로 전환한다.
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        if (reservation.getVisitDate() == null || reservation.getEntryStartTime() == null) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }

        int deadlineHours = reservation.getCancelDeadlineHours() == null
                ? DEFAULT_CANCEL_DEADLINE_HOURS
                : reservation.getCancelDeadlineHours();
        if (deadlineHours < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDateTime deadline = LocalDateTime.of(reservation.getVisitDate(), reservation.getEntryStartTime())
                .minusHours(deadlineHours);
        if (now.isAfter(deadline)) {
            throw new CommonException(ErrorCode.RESERVATION_CANCEL_DEADLINE_EXCEEDED);
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
