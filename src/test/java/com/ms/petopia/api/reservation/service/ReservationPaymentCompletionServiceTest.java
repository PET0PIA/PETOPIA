package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.PaymentConfirmationReservationRow;
import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletedCommand;
import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletionResponse;
import com.ms.petopia.api.reservation.dto.ReservationPaymentReceiptRow;
import com.ms.petopia.api.reservation.mapper.ReservationPaymentConfirmationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationPaymentCompletionServiceTest {

    private static final Long FAIR_ID = 100L;
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 8, 1, 10, 5);
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 8, 1, 10, 5, 1);
    private static final ReservationPaymentCompletedCommand COMMAND =
            new ReservationPaymentCompletedCommand("event-1", 20L, 10L, 15_000L, PAID_AT);

    @Mock
    private ReservationPaymentConfirmationMapper mapper;
    @Mock
    private EntryQrService entryQrService;
    @Mock
    private ReservationTimeProvider timeProvider;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private ReservationPaymentCompletionService service;

    @Test
    void confirmsPendingReservationAndIssuesQr() {
        given(mapper.selectReservationForUpdate(10L)).willReturn(pendingReservation(15_000));
        given(timeProvider.now()).willReturn(RECEIVED_AT);
        given(mapper.confirmPendingReservation(10L, PAID_AT, RECEIVED_AT)).willReturn(1);
        given(entryQrService.issueForPaymentCompletion(10L)).willReturn("qr-token");

        ReservationPaymentCompletionResponse response = service.complete(COMMAND);

        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.idempotentReplay()).isFalse();
        assertThat(response.entryQrToken()).isEqualTo("qr-token");
        verify(mapper).insertReceipt(COMMAND, RECEIVED_AT);
        verify(mapper).insertConfirmedHistory(10L, 20L, 15_000L, RECEIVED_AT);
    }

    @Test
    void returnsSameResultForSameEventReplay() {
        ReservationPaymentReceiptRow receipt = new ReservationPaymentReceiptRow();
        receipt.setEventId("event-1");
        receipt.setPaymentId(20L);
        receipt.setReservationId(10L);
        receipt.setPaidAmount(15_000);
        receipt.setPaidAt(PAID_AT);
        given(mapper.selectReceiptByEventId("event-1")).willReturn(receipt);
        given(entryQrService.issueForPaymentCompletion(10L)).willReturn("qr-token");

        ReservationPaymentCompletionResponse response = service.complete(COMMAND);

        assertThat(response.idempotentReplay()).isTrue();
        verify(mapper, never()).selectReservationForUpdate(10L);
        verify(mapper, never()).insertReceipt(COMMAND, RECEIVED_AT);
    }

    @Test
    void returnsConfirmedWithoutQrWhenReplayArrivesAfterEntryEnds() {
        ReservationPaymentReceiptRow receipt = new ReservationPaymentReceiptRow();
        receipt.setEventId("event-1");
        receipt.setPaymentId(20L);
        receipt.setReservationId(10L);
        receipt.setPaidAmount(15_000);
        receipt.setPaidAt(PAID_AT);
        given(mapper.selectReceiptByEventId("event-1")).willReturn(receipt);
        given(entryQrService.issueForPaymentCompletion(10L)).willReturn(null);

        ReservationPaymentCompletionResponse response = service.complete(COMMAND);

        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.idempotentReplay()).isTrue();
        assertThat(response.entryQrToken()).isNull();
    }

    @Test
    void rejectsAmountMismatchBeforeWritingReceipt() {
        given(mapper.selectReservationForUpdate(10L)).willReturn(pendingReservation(20_000));

        assertError(() -> service.complete(COMMAND), ErrorCode.RESERVATION_PAYMENT_AMOUNT_MISMATCH);
        verify(mapper, never()).insertReceipt(COMMAND, RECEIVED_AT);
    }

    @Test
    void rejectsReceiptThatViolatesUniqueConstraint() {
        given(mapper.selectReservationForUpdate(10L)).willReturn(pendingReservation(15_000));
        given(timeProvider.now()).willReturn(RECEIVED_AT);
        willThrow(new DuplicateKeyException("UK_RESERVATION_PAYMENT_PAYMENT"))
                .given(mapper).insertReceipt(COMMAND, RECEIVED_AT);

        assertError(() -> service.complete(COMMAND), ErrorCode.RESERVATION_PAYMENT_EVENT_CONFLICT);
        verify(mapper, never()).confirmPendingReservation(10L, PAID_AT, RECEIVED_AT);
    }

    @Test
    void rejectsPaymentAfterReservationDeadline() {
        PaymentConfirmationReservationRow row = pendingReservation(15_000);
        row.setPaymentExpiresAt(PAID_AT.minusSeconds(1));
        given(mapper.selectReservationForUpdate(10L)).willReturn(row);

        assertError(() -> service.complete(COMMAND), ErrorCode.RESERVATION_PAYMENT_EXPIRED);
    }

    @Test
    void rejectsPaymentAtExactReservationDeadline() {
        PaymentConfirmationReservationRow row = pendingReservation(15_000);
        row.setPaymentExpiresAt(PAID_AT);
        given(mapper.selectReservationForUpdate(10L)).willReturn(row);

        assertError(() -> service.complete(COMMAND), ErrorCode.RESERVATION_PAYMENT_EXPIRED);
    }

    private PaymentConfirmationReservationRow pendingReservation(long amount) {
        PaymentConfirmationReservationRow row = new PaymentConfirmationReservationRow();
        row.setReservationId(10L);
        row.setFairId(FAIR_ID);
        row.setStatus("PENDING_PAYMENT");
        row.setReservationAmount(amount);
        row.setPaymentExpiresAt(PAID_AT.plusMinutes(5));
        return row;
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
