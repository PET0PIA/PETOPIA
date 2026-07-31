package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.CreateReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.dto.ReservationCreationContext;
import com.ms.petopia.api.reservation.dto.ReservationInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.api.reservation.model.ReservationType;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String CONFIRMED = "CONFIRMED";
    private static final long PAYMENT_WAIT_MINUTES = 10;

    private final ReservationMapper reservationMapper;
    private final ReservationNumberGenerator reservationNumberGenerator;
    private final ReservationTimeProvider timeProvider;
    private final EntryQrService entryQrService;

    /**
     * 본인 1인 1매 예약을 생성한다.
     *
     * <p>유료 예약은 결제 대기 상태까지만 만든다. 결제 준비·승인과 QR 발급은
     * 결제/입장 도메인의 후속 단계에서 처리한다.
     */
    @Transactional
    public CreateReservationResponse create(
            Long fairId,
            Long userId,
            CreateReservationRequest request
    ) {
        validateIdentifiersAndRequest(fairId, userId, request);

        ReservationCreationContext context =
                reservationMapper.selectCreationContextForUpdate(fairId, request.visitDate());
        if (context == null) {
            if (!reservationMapper.existsFair(fairId)) {
                throw new CommonException(ErrorCode.RESERVATION_FAIR_NOT_FOUND);
            }
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }

        LocalDate today = timeProvider.today();
        validateFair(context, today);

        if (reservationMapper.existsActiveReservation(fairId, userId)) {
            throw new CommonException(ErrorCode.DUPLICATED_RESERVATION);
        }

        int occupied = reservationMapper.countCapacityOccupyingReservations(fairId, request.visitDate());
        if (occupied >= context.getCapacity()) {
            throw new CommonException(ErrorCode.RESERVATION_SOLD_OUT);
        }

        ReservationUserSnapshot user = reservationMapper.selectUserSnapshot(userId);
        validateUser(user);

        boolean paymentRequired = context.getReservationFee() > 0;
        validateTerms(paymentRequired, request);

        LocalDateTime now = timeProvider.now();
        String status = paymentRequired ? PENDING_PAYMENT : CONFIRMED;
        LocalDateTime paymentExpiresAt = paymentRequired ? now.plusMinutes(PAYMENT_WAIT_MINUTES) : null;
        String reservationNo = reservationNumberGenerator.generate(today);

        ReservationInsertRow row = ReservationInsertRow.builder()
                .reservationNo(reservationNo)
                .fairId(fairId)
                .userId(userId)
                .visitDate(request.visitDate())
                .reservationType(ReservationType.ADVANCE.name())
                .status(status)
                .reservationAmount(context.getReservationFee())
                .reserverName(user.getNickname())
                .reserverPhone(user.getPhone())
                .reserverEmail(user.getEmail())
                .channel("ONLINE")
                .agreedTerms(paymentRequired)
                .reservationTermsVersion(paymentRequired ? request.reservationTermsVersion().trim() : null)
                .reservationTermsAgreedAt(paymentRequired ? now : null)
                .reservedAt(paymentRequired ? null : now)
                .paymentExpiresAt(paymentExpiresAt)
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            reservationMapper.insertReservation(row);
        } catch (DuplicateKeyException e) {
            if (ReservationConstraintViolations.isActiveReservationDuplicate(e)) {
                throw new CommonException(ErrorCode.DUPLICATED_RESERVATION, e);
            }
            throw e;
        }

        reservationMapper.insertCreatedHistory(row.getReservationId(), userId, status);
        String entryQrToken = paymentRequired ? null : entryQrService.issueForReservation(row.getReservationId());

        return new CreateReservationResponse(
                row.getReservationId(),
                reservationNo,
                ReservationType.ADVANCE.name(),
                status,
                context.getReservationFee(),
                paymentRequired,
                paymentExpiresAt,
                entryQrToken
        );
    }

    private void validateIdentifiersAndRequest(
            Long fairId,
            Long userId,
            CreateReservationRequest request
    ) {
        if (fairId == null || fairId <= 0 || userId == null || userId <= 0
                || request == null || request.visitDate() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateFair(ReservationCreationContext context, LocalDate today) {
        if (context.getPublishedAt() == null
                || context.getCanceledAt() != null
                || context.getReservationStartDate() == null
                || context.getReservationEndDate() == null
                || today.isBefore(context.getReservationStartDate())
                || today.isAfter(context.getReservationEndDate())) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_OPEN);
        }

        if (context.getOperationDate().isBefore(today)) {
            throw new CommonException(ErrorCode.RESERVATION_DATE_NOT_AVAILABLE);
        }

        if (context.getReservationFee() < 0 || context.getCapacity() <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateUser(ReservationUserSnapshot user) {
        if (user == null) {
            throw new CommonException(ErrorCode.USER_NOT_FOUND);
        }
        if (!"ACTIVE".equals(user.getStatus()) || !"USER".equals(user.getRole())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void validateTerms(boolean paymentRequired, CreateReservationRequest request) {
        if (!paymentRequired) {
            return;
        }

        if (!Boolean.TRUE.equals(request.reservationTermsAgreed())
                || request.reservationTermsVersion() == null
                || request.reservationTermsVersion().isBlank()) {
            throw new CommonException(ErrorCode.RESERVATION_TERMS_REQUIRED);
        }
    }
}
