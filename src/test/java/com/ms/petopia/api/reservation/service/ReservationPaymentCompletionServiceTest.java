package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.dto.PaymentConfirmationReservationRow;
import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletedCommand;
import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletionResponse;
import com.ms.petopia.api.reservation.dto.ReservationPaymentReceiptRow;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.service.MailService;
import com.ms.petopia.api.reservation.dto.ReservationListRow;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.api.reservation.mapper.ReservationPaymentConfirmationMapper;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationPaymentCompletionServiceTest {

    private static final Long FAIR_ID = 100L;
    private static final Long USER_ID = 42L;
    private static final LocalDateTime PAID_AT = LocalDateTime.of(2026, 8, 1, 10, 5);
    private static final LocalDateTime RECEIVED_AT = LocalDateTime.of(2026, 8, 1, 10, 5, 1);
    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 9, 12);
    private static final LocalTime ENTRY_START = LocalTime.of(10, 0);
    private static final LocalTime ENTRY_END = LocalTime.of(18, 0);
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
    @Mock
    private NotificationService notificationService;
    @Mock
    private ThreadPoolTaskExecutor mailExecutor;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private AuthMapper authMapper;
    @Mock
    private MailService mailService;
    @InjectMocks
    private ReservationPaymentCompletionService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void confirmsPendingReservationAndIssuesQr() {
        given(mapper.selectReservationForUpdate(10L)).willReturn(pendingReservation(15_000));
        given(timeProvider.now()).willReturn(RECEIVED_AT);
        given(mapper.confirmPendingReservation(10L, PAID_AT, RECEIVED_AT)).willReturn(1);
        given(entryQrService.issueForPaymentCompletion(10L)).willReturn("qr-token");
        given(authMapper.selectUserById(USER_ID)).willReturn(userWithEmail());
        given(reservationMapper.selectReservationForOwner(10L, USER_ID)).willReturn(confirmedRow());

        ReservationPaymentCompletionResponse response = service.complete(COMMAND);

        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.idempotentReplay()).isFalse();
        assertThat(response.entryQrToken()).isEqualTo("qr-token");
        verify(mapper).insertReceipt(COMMAND, RECEIVED_AT);
        verify(mapper).insertConfirmedHistory(10L, 20L, 15_000L, RECEIVED_AT);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<SaveNotificationDto.Request> notifCaptor =
                ArgumentCaptor.forClass(SaveNotificationDto.Request.class);
        verify(notificationService).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(notifCaptor.getValue().type()).isEqualTo(NotificationType.RESERVATION_CONFIRMED);

        // 메일은 요청 스레드에서 직접 나가지 않고 전용 풀로 넘어가야 한다.
        // 제출만 확인하면 정작 발송 본문이 한 번도 실행되지 않으므로, 넘긴 작업을 꺼내 돌린다.
        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mailExecutor).execute(taskCaptor.capture());
        taskCaptor.getValue().run();

        verify(mailService).sendReservationConfirmedEmail(
                "buyer@petopia.test", "R-0001", "2026 서울 펫페어", "사전예약",
                VISIT_DATE, ENTRY_START, ENTRY_END, 15_000L, RECEIVED_AT, "qr-token");
    }

    /**
     * afterCommit의 예약 조회가 일시적으로 실패해도 워커에서 다시 조회해 메일을 살린다.
     *
     * 조회 한 번 실패로 입장 QR이 담긴 메일이 영구 유실되면 손해가 크다 - 재시도 장치가 없기
     * 때문이다. 재조회가 워커에서 일어나므로 응답 시간에는 영향이 없다.
     */
    @Test
    void retriesReservationLookupInWorkerWhenFirstLookupFails() {
        given(mapper.selectReservationForUpdate(10L)).willReturn(pendingReservation(15_000));
        given(timeProvider.now()).willReturn(RECEIVED_AT);
        given(mapper.confirmPendingReservation(10L, PAID_AT, RECEIVED_AT)).willReturn(1);
        given(entryQrService.issueForPaymentCompletion(10L)).willReturn("qr-token");
        given(authMapper.selectUserById(USER_ID)).willReturn(userWithEmail());
        // 첫 조회(afterCommit)는 실패, 두 번째 조회(워커의 재시도)는 성공.
        given(reservationMapper.selectReservationForOwner(10L, USER_ID))
                .willThrow(new RuntimeException("일시적 DB 오류"))
                .willReturn(confirmedRow());

        service.complete(COMMAND);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);

        ArgumentCaptor<Runnable> taskCaptor = ArgumentCaptor.forClass(Runnable.class);
        verify(mailExecutor).execute(taskCaptor.capture());
        taskCaptor.getValue().run();

        // 재조회로 살아난 정보로 메일이 나갔다.
        verify(mailService).sendReservationConfirmedEmail(
                "buyer@petopia.test", "R-0001", "2026 서울 펫페어", "사전예약",
                VISIT_DATE, ENTRY_START, ENTRY_END, 15_000L, RECEIVED_AT, "qr-token");
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

    private User userWithEmail() {
        return User.builder().email("buyer@petopia.test").build();
    }

    private ReservationListRow confirmedRow() {
        ReservationListRow row = new ReservationListRow();
        row.setReservationId(10L);
        row.setReservationNo("R-0001");
        row.setFairName("2026 서울 펫페어");
        row.setReservationType("ADVANCE");
        row.setVisitDate(VISIT_DATE);
        row.setEntryStartTime(ENTRY_START);
        row.setEntryEndTime(ENTRY_END);
        row.setAmount(15_000L);
        row.setReservedAt(RECEIVED_AT);
        return row;
    }

    private PaymentConfirmationReservationRow pendingReservation(long amount) {
        PaymentConfirmationReservationRow row = new PaymentConfirmationReservationRow();
        row.setReservationId(10L);
        row.setFairId(FAIR_ID);
        row.setUserId(USER_ID);
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
