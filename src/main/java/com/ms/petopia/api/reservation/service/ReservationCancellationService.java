package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.dto.CancelReservationRequest;
import com.ms.petopia.api.reservation.dto.CancelReservationResponse;
import com.ms.petopia.api.reservation.dto.ReservationCancellationContext;
import com.ms.petopia.api.reservation.mapper.ReservationCancellationMapper;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent; // 실시간 통계 확인용
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher; // 실시간 통계 확인용
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
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
    private final ApplicationEventPublisher eventPublisher; // 실시간 통계 확인용
    private final NotificationService notificationService;

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

        eventPublisher.publishEvent(new ReservationStatusChangedEvent(reservation.getFairId())); // 실시간 통계 확인용

        Long notifyUserId = reservation.getUserId();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationService.save(new SaveNotificationDto.Request(
                            notifyUserId,
                            RecipientType.USER,
                            NotificationType.RESERVATION_CANCELED,
                            "예약이 취소되었습니다",
                            "예약이 정상적으로 취소 처리되었습니다.",
                            null,
                            List.of(DeliveryChannel.IN_APP),
                            null
                    ));
                } catch (Exception e) {
                    log.error("예약 취소 알림 저장 실패. userId={}, reservationId={}", notifyUserId, reservationId, e);
                }
            }
        });

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
