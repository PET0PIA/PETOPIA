package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationPaymentContextResponse;
import com.ms.petopia.api.reservation.dto.ReservationPaymentContextRow;
import com.ms.petopia.api.reservation.mapper.ReservationPaymentConfirmationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReservationPaymentContextService {

    private final ReservationPaymentConfirmationMapper mapper;
    private final ReservationTimeProvider timeProvider;

    /** 결제 도메인에 예약 원장의 확정 금액과 제한시각만 제공한다. */
    @Transactional(readOnly = true)
    public ReservationPaymentContextResponse getPayableContext(Long reservationId) {
        if (reservationId == null || reservationId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        ReservationPaymentContextRow row = mapper.selectPaymentContext(reservationId);
        if (row == null) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (!"PENDING_PAYMENT".equals(row.getStatus())) {
            if ("EXPIRED".equals(row.getStatus())) {
                throw new CommonException(ErrorCode.RESERVATION_PAYMENT_EXPIRED);
            }
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        if (row.getReservationAmount() <= 0 || row.getPaymentExpiresAt() == null) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        if (!timeProvider.now().isBefore(row.getPaymentExpiresAt())) {
            throw new CommonException(ErrorCode.RESERVATION_PAYMENT_EXPIRED);
        }

        return new ReservationPaymentContextResponse(
                row.getReservationId(),
                row.getFairId(),
                row.getUserId(),
                row.getReservationType(),
                row.getReservationAmount(),
                row.getPaymentExpiresAt()
        );
    }
}
