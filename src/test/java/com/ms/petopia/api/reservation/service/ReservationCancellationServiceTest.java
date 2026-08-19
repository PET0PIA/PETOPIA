package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.dto.CancelReservationRequest;
import com.ms.petopia.api.reservation.dto.CancelReservationResponse;
import com.ms.petopia.api.reservation.dto.ReservationCancellationContext;
import com.ms.petopia.api.reservation.mapper.ReservationCancellationMapper;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationCancellationServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long RESERVATION_ID = 30L;
    private static final Long USER_ID = 20L;
    private static final Long PAYMENT_ID = 40L;
    private static final Long REFUND_ID = 50L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 21, 0);

    @Mock
    private ReservationCancellationMapper cancellationMapper;
    @Mock
    private ReservationCapacityMapper capacityMapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private PaymentService paymentService;
    @Mock
    private RefundService refundService;
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
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(null);
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "PENDING_PAYMENT", "일정 변경", USER_ID, NOW
        )).willReturn(1);

        CancelReservationResponse response = service.cancel(
                RESERVATION_ID, USER_ID, new CancelReservationRequest("일정 변경")
        );

        assertThat(response.reservationStatus()).isEqualTo("CANCELED");
        assertThat(response.refunded()).isFalse();
        verify(cancellationMapper).insertCanceledHistory(
                RESERVATION_ID, "PENDING_PAYMENT", "일정 변경", USER_ID, null, null, NOW
        );

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<SaveNotificationDto.Request> notifCaptor =
                ArgumentCaptor.forClass(SaveNotificationDto.Request.class);
        verify(notificationService).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(notifCaptor.getValue().type()).isEqualTo(NotificationType.RESERVATION_CANCELED);
        verifyNoInteractions(refundService);
    }

    @Test
    void cancelsPendingPaymentLedgerRowTogetherWithReservation() {
        ReservationCancellationContext context = context("PENDING_PAYMENT", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(payment("PENDING"));
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "PENDING_PAYMENT", null, USER_ID, NOW
        )).willReturn(1);

        service.cancel(RESERVATION_ID, USER_ID, null);

        verify(paymentService).cancelPayment(PAYMENT_ID, "RESERVATION");
        verifyNoInteractions(refundService);
    }

    @Test
    void cancelsWaitingForDepositPaymentLedgerRowTogetherWithReservation() {
        // 가상계좌 발급까지는 됐지만 아직 입금 전인 상태 — 아직 실제 돈은 안 움직였으므로
        // PENDING과 동일하게 예약 취소와 함께 정리돼야 한다(2026-08-18 CodeRabbit 리뷰 지적,
        // 이 상태를 못 다뤄서 예약은 취소됐는데 결제만 계속 대기로 남던 사각지대였음).
        ReservationCancellationContext context = context("PENDING_PAYMENT", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(payment("WAITING_FOR_DEPOSIT"));
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "PENDING_PAYMENT", null, USER_ID, NOW
        )).willReturn(1);

        service.cancel(RESERVATION_ID, USER_ID, null);

        verify(paymentService).cancelPayment(PAYMENT_ID, "RESERVATION");
        verifyNoInteractions(refundService);
    }

    @Test
    void rejectsCancellationWhilePaymentApprovalIsInFlight() {
        ReservationCancellationContext context = context("PENDING_PAYMENT", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(payment("PROCESSING"));

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null),
                ErrorCode.RESERVATION_PAYMENT_IN_PROGRESS);

        verify(cancellationMapper, never()).cancelReservation(any(), any(), any(), any(), any());
        verify(paymentService, never()).cancelPayment(any(), any());
    }

    @Test
    void leavesAlreadyFinishedPaymentLedgerRowAlone() {
        ReservationCancellationContext context = context("PENDING_PAYMENT", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(payment("EXPIRED"));
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "PENDING_PAYMENT", null, USER_ID, NOW
        )).willReturn(1);

        service.cancel(RESERVATION_ID, USER_ID, null);

        verify(paymentService, never()).cancelPayment(any(), any());
    }

    @Test
    void cancelsFreeAdvanceReservationBeforeDefaultDeadline() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 0);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, NOW
        )).willReturn(1);

        CancelReservationResponse response = service.cancel(RESERVATION_ID, USER_ID, null);

        assertThat(response.refunded()).isFalse();
        verify(cancellationMapper).cancelReservation(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, NOW
        );
        verifyNoInteractions(refundService, paymentMapper, paymentService);
    }

    @Test
    void refundsDepositWhenPaidConfirmedReservationIsCanceled() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(payment("COMPLETED"));
        given(refundService.refundOrReuse(eq(PAYMENT_ID), eq(USER_ID), any())).willReturn(refundResponse());
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, NOW
        )).willReturn(1);

        CancelReservationResponse response = service.cancel(RESERVATION_ID, USER_ID, null);

        assertThat(response.reservationStatus()).isEqualTo("CANCELED");
        assertThat(response.refunded()).isTrue();
        assertThat(response.refundId()).isEqualTo(REFUND_ID);
        assertThat(response.refundAmount()).isEqualTo(10_000L);
        assertThat(response.refundStatus()).isEqualTo("COMPLETED");

        verify(refundService).refundOrReuse(
                PAYMENT_ID, USER_ID,
                new RefundRequest(RefundReason.USER_CANCEL, RequestedByDomain.RESERVATION)
        );
        verify(cancellationMapper).insertCanceledHistory(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, REFUND_ID, 10_000L, NOW
        );
    }

    /**
     * 잠금 밖에서 findByPaymentId로 미리 걸러내면, 그 조회와 환불 사이에 행사 일괄환불이 커밋되는
     * 창이 남는다(REFUND_ALREADY_PROCESSED → 취소까지 롤백). 그래서 예약 취소는 사전 조회를 하지
     * 않고 판단 전체를 refundOrReuse(결제 행 잠금 뒤 처리)에 맡긴다.
     */
    @Test
    void delegatesRefundDecisionToLockedApiWithoutPreCheck() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(payment("COMPLETED"));
        given(refundService.refundOrReuse(eq(PAYMENT_ID), eq(USER_ID), any())).willReturn(refundResponse());
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, NOW
        )).willReturn(1);

        service.cancel(RESERVATION_ID, USER_ID, null);

        verify(refundService, never()).findByPaymentId(any());
        verify(refundService, never()).refund(any(), any(), any());
    }

    /**
     * 행사 일괄환불이 먼저 커밋한 상태에서 사용자가 취소하는 경우. refundOrReuse가 기존 환불을
     * 돌려주므로 예약 취소는 정상 완료되고, 응답에는 먼저 만들어진 환불(FAIR_CANCEL_USER)이 실린다.
     */
    @Test
    void completesCancellationWhenFairBatchAlreadyRefundedTheDeposit() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(payment("COMPLETED"));
        given(refundService.refundOrReuse(eq(PAYMENT_ID), eq(USER_ID), any()))
                .willReturn(reusedRefundResponse());
        given(cancellationMapper.cancelReservation(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, NOW
        )).willReturn(1);

        CancelReservationResponse response = service.cancel(RESERVATION_ID, USER_ID, null);

        assertThat(response.reservationStatus()).isEqualTo("CANCELED");
        assertThat(response.refunded()).isTrue();
        assertThat(response.refundId()).isEqualTo(REFUND_ID);
        // 취소 이력에도 재사용된 환불이 그대로 기록된다.
        verify(cancellationMapper).insertCanceledHistory(
                RESERVATION_ID, "CONFIRMED", null, USER_ID, REFUND_ID, 10_000L, NOW
        );
    }

    /**
     * 반대로 환불이 끝까지 거부되면(확정 정산에 포함된 결제 등) 예약 취소도 함께 실패해야 한다 —
     * 돈은 그대로인데 예약만 CANCELED가 되는 상태를 만들지 않는다.
     */
    @Test
    void failsCancellationWhenRefundIsRejected() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(payment("COMPLETED"));
        given(refundService.refundOrReuse(eq(PAYMENT_ID), eq(USER_ID), any()))
                .willThrow(new CommonException(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE));

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null),
                ErrorCode.REFUND_TARGET_NOT_REFUNDABLE);

        verify(cancellationMapper, never()).cancelReservation(any(), any(), any(), any(), any());
        verify(cancellationMapper, never()).insertCanceledHistory(
                any(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void rejectsPaidCancellationWhenDepositPaymentIsMissing() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);
        given(paymentMapper.selectByReservationId(RESERVATION_ID)).willReturn(null);

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null),
                ErrorCode.RESERVATION_REFUND_PAYMENT_NOT_FOUND);

        verify(cancellationMapper, never()).cancelReservation(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsPaidCancellationAfterDeadlineWithoutTouchingRefund() {
        ReservationCancellationContext context = context("CONFIRMED", "ADVANCE", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 4, 22, 0, 1));

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null),
                ErrorCode.RESERVATION_CANCEL_DEADLINE_EXCEEDED);

        verifyNoInteractions(refundService, paymentMapper, paymentService);
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
                any(), any(), any(), any(), any(), any(), any()
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
    void rejectsOnsiteReservation() {
        ReservationCancellationContext context = context("CONFIRMED", "ONSITE_DIRECT", 0);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null),
                ErrorCode.RESERVATION_STATUS_CONFLICT);
    }

    @Test
    void rejectsPaidOnsiteReservationWithoutRefunding() {
        ReservationCancellationContext context = context("CONFIRMED", "ONSITE_DIRECT", 10_000);
        given(cancellationMapper.selectCancellationContextForUpdate(RESERVATION_ID)).willReturn(context);
        given(timeProvider.now()).willReturn(NOW);

        assertError(() -> service.cancel(RESERVATION_ID, USER_ID, null),
                ErrorCode.RESERVATION_STATUS_CONFLICT);

        verifyNoInteractions(refundService, paymentMapper, paymentService);
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

    private PaymentRow payment(String status) {
        PaymentRow payment = new PaymentRow();
        payment.setPaymentId(PAYMENT_ID);
        payment.setPaymentType("RESERVATION_DEPOSIT");
        payment.setReservationId(RESERVATION_ID);
        payment.setPayerUserId(USER_ID);
        payment.setAmount(10_000L);
        payment.setStatus(status);
        return payment;
    }

    private RefundRow refundRow() {
        RefundRow row = new RefundRow();
        row.setRefundId(REFUND_ID);
        row.setPaymentId(PAYMENT_ID);
        row.setReservationId(RESERVATION_ID);
        row.setRefundReason(RefundReason.USER_CANCEL.name());
        row.setRequestedByDomain(RequestedByDomain.RESERVATION.name());
        row.setRefundAmount(10_000L);
        row.setStatus("COMPLETED");
        row.setRequestedAt(NOW);
        row.setProcessedAt(NOW);
        return row;
    }

    private RefundResponse refundResponse() {
        return RefundResponse.from(refundRow());
    }

    /** 행사 취소 일괄환불이 먼저 만들어둔 환불을 refundOrReuse가 재사용해 돌려준 형태. */
    private RefundResponse reusedRefundResponse() {
        RefundRow row = refundRow();
        row.setRefundReason(RefundReason.FAIR_CANCEL_USER.name());
        row.setRequestedByDomain(RequestedByDomain.FAIR.name());
        return RefundResponse.from(row);
    }

    private void assertError(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(errorCode);
    }
}
