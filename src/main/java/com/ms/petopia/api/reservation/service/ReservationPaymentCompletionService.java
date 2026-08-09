package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.dto.PaymentConfirmationReservationRow;
import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletedCommand;
import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletionResponse;
import com.ms.petopia.api.reservation.dto.ReservationPaymentReceiptRow;
import com.ms.petopia.api.reservation.mapper.ReservationPaymentConfirmationMapper;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent; // 실시간 통계 확인용
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher; // 실시간 통계 확인용
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationPaymentCompletionService {

    private final ReservationPaymentConfirmationMapper confirmationMapper;
    private final EntryQrService entryQrService;
    private final ReservationTimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher; // 실시간 통계 확인용
    private final NotificationService notificationService;

    /**
     * 결제 도메인이 검증한 성공 통지를 예약 상태에 반영한다.
     * 이 메서드는 결제 API를 호출하거나 결제 테이블을 조회하지 않는다.
     */
    @Transactional
    public ReservationPaymentCompletionResponse complete(ReservationPaymentCompletedCommand command) {
        validateCommand(command);

        ReservationPaymentReceiptRow replay = confirmationMapper.selectReceiptByEventId(command.eventId());
        if (replay != null) {
            assertSameEvent(replay, command);
            return new ReservationPaymentCompletionResponse(
                    replay.getReservationId(),
                    "CONFIRMED",
                    true,
                    entryQrService.issueForPaymentCompletion(replay.getReservationId())
            );
        }

        PaymentConfirmationReservationRow reservation =
                confirmationMapper.selectReservationForUpdate(command.reservationId());
        if (reservation == null) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_FOUND);
        }

        ReservationPaymentReceiptRow existing =
                confirmationMapper.selectReceiptByReservationId(command.reservationId());
        if (existing != null) {
            assertSameEvent(existing, command);
            return new ReservationPaymentCompletionResponse(
                    existing.getReservationId(),
                    "CONFIRMED",
                    true,
                    entryQrService.issueForPaymentCompletion(existing.getReservationId())
            );
        }
        if ("EXPIRED".equals(reservation.getStatus())) {
            throw new CommonException(ErrorCode.RESERVATION_PAYMENT_EXPIRED);
        }
        if (!"PENDING_PAYMENT".equals(reservation.getStatus())) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        if (reservation.getReservationAmount() != command.paidAmount()) {
            throw new CommonException(ErrorCode.RESERVATION_PAYMENT_AMOUNT_MISMATCH);
        }
        if (reservation.getPaymentExpiresAt() == null
                || !command.paidAt().isBefore(reservation.getPaymentExpiresAt())) {
            throw new CommonException(ErrorCode.RESERVATION_PAYMENT_EXPIRED);
        }

        LocalDateTime receivedAt = timeProvider.now();
        try {
            confirmationMapper.insertReceipt(command, receivedAt);
        } catch (DuplicateKeyException exception) {
            // event_id / reservation_id / payment_id 중 하나가 이미 소진된 경우다.
            // 앞선 조회를 통과했다면 동시 통지이거나, 다른 예약에 쓰인 결제를 재사용한 통지다.
            throw new CommonException(ErrorCode.RESERVATION_PAYMENT_EVENT_CONFLICT, exception);
        }
        int updated = confirmationMapper.confirmPendingReservation(
                command.reservationId(),
                command.paidAt(),
                receivedAt
        );
        if (updated != 1) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        confirmationMapper.insertConfirmedHistory(
                command.reservationId(),
                command.paymentId(),
                command.paidAmount(),
                receivedAt
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
                            NotificationType.RESERVATION_CONFIRMED,
                            "예약이 확정되었습니다",
                            "결제가 완료되어 예약이 확정되었습니다.",
                            null,
                            List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                            null
                    ));
                } catch (Exception e) {
                    log.error("예약 확정 알림 저장 실패. userId={}, reservationId={}",
                            notifyUserId, command.reservationId(), e);
                }
            }
        });

        return new ReservationPaymentCompletionResponse(
                command.reservationId(),
                "CONFIRMED",
                false,
                entryQrService.issueForPaymentCompletion(command.reservationId())
        );
    }

    private void validateCommand(ReservationPaymentCompletedCommand command) {
        if (command == null
                || command.eventId() == null || command.eventId().isBlank()
                || command.eventId().length() > 100
                || command.paymentId() == null || command.paymentId() <= 0
                || command.reservationId() == null || command.reservationId() <= 0
                || command.paidAmount() == null || command.paidAmount() <= 0
                || command.paidAt() == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void assertSameEvent(
            ReservationPaymentReceiptRow receipt,
            ReservationPaymentCompletedCommand command
    ) {
        if (!receipt.getPaymentId().equals(command.paymentId())
                || !receipt.getReservationId().equals(command.reservationId())
                || receipt.getPaidAmount() != command.paidAmount()
                || !receipt.getPaidAt().equals(command.paidAt())) {
            throw new CommonException(ErrorCode.RESERVATION_PAYMENT_EVENT_CONFLICT);
        }
    }
}
