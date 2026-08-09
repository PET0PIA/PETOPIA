package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.CancelReservationRequest;
import com.ms.petopia.api.reservation.dto.CancelReservationResponse;
import com.ms.petopia.api.reservation.dto.ReservationCancellationContext;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.mapper.ReservationCancellationMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long RESERVATION_ID = 30L;
    private static final Long USER_ID = 20L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 21, 0);

    @Mock
    private ReservationCancellationMapper cancellationMapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private NotificationService notificationService;
    @InjectMocks
    private ReservationCancellationService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void cancelsPaymentPendingReservationImmediately() {
        ReservationCancellationContext context = context("PENDING_PAYMENT", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "PENDING_PAYMENT", "일정 변경", USER_ID, NOW
        )).willReturn(1);

        CancelReservationResponse response = service.cancel(
                RESERVATION_ID, USER_ID, new CancelReservationRequest("일정 변경")
        );

        assertThat(response.reservationStatus()).isEqualTo("CANCELED");
        verify(cancellationMapper).insertCanceledHistory(
                RESERVATION_ID, "PENDING_PAYMENT", "일정 변경", USER_ID, NOW
        );
    }

    @Test
    void cancelsFreeAdvanceReservationBeforeDefaultDeadline() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 0);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, NOW
        )).willReturn(1);

        service.cancel(RESERVATION_ID, USER_ID, null);

        verify(cancellationMapper).cancelReservation(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, NOW
        );
    }

    @Test
    void rejectsCancellationRequestFromAnotherUser() {
        ReservationCancellationContext context = context("PENDING_PAYMENT", "ADVANCE", 10_000);
        context.setUserId(99L);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null), ErrorCode.ACCESS_DENIED);

        verify(cancellationMapper, never()).cancelReservation(
                any(), any(), any(), any(), any()
        );
        verify(cancellationMapper, never()).insertCanceledHistory(
                any(), any(), any(), any(), any()
        );
    }

    @Test
    void rejectsFreeAdvanceCancellationAfterDeadline() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 0);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 4, 22, 0, 1));

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null),
                ErrorCode.RESERVATION_CANCEL_DEADLINE_EXCEEDED);

        verify(cancellationMapper, never()).cancelReservation(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, LocalDateTime.of(2026, 8, 4, 22, 0, 1)
        );
    }

    @Test
    void rejectsPaidConfirmedReservationUntilPaymentRefundIntegrationExists() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null),
                ErrorCode.RESERVATION_STATUS_CONFLICT);

        verify(cancellationMapper, never()).cancelReservation(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, NOW
        );
    }

    @Test
    void rejectsOnsiteReservation() {
        ReservationCancellationContext context = context("CONFIRMED", "ONSITE_DIRECT", 0);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null),
                ErrorCode.RESERVATION_STATUS_CONFLICT);
    }

    private ReservationCancellationContext context(String status, String reservationType, long amount) {
        ReservationCancellationContext context = new ReservationCancellationContext();
        context.setReservationId(RESERVATION_ID);
        context.setFairId(FAIR_ID);
        context.setUserId(USER_ID);
        context.setVisitDate(LocalDate.of(2026, 8, 5));
        context.setEntryStartTime(LocalTime.of(10, 0));
        context.setStatus(status);
        context.setReservationType(reservationType);
        context.setReservationAmount(amount);
        return context;
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
