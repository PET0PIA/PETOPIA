package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.dto.CanceledFairReservationRow;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import com.ms.petopia.api.reservation.mapper.ReservationFairCancelMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ReservationFairCancelSyncServiceTest {

    private static final int BATCH = 200;
    private static final Long RESERVATION_ID = 7L;
    private static final Long USER_ID = 3L;
    private static final Long FAIR_ID = 11L;
    private static final LocalDate VISIT_DATE = LocalDate.of(2026, 9, 5);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 22, 10, 0);
    private static final String REASON = ReservationFairCancelSyncService.CANCEL_REASON;

    @Mock
    private ReservationFairCancelMapper fairCancelMapper;
    @Mock
    private ReservationCapacityMapper capacityMapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private ReservationFairCancelSyncService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    @DisplayName("무료 예약은 환불을 기다리지 않고 바로 취소하고 알린다")
    void cancelsFreeReservationImmediately() {
        given(timeProvider.now()).willReturn(NOW);
        given(fairCancelMapper.selectLiveReservationsOfCanceledFairsForUpdate(BATCH))
                .willReturn(List.of(row("CONFIRMED", null, null)));
        given(fairCancelMapper.cancelByFairCancellation(RESERVATION_ID, "CONFIRMED", REASON, false, NOW))
                .willReturn(1);

        assertThat(service.cancelReservationsOfCanceledFairs(BATCH)).isEqualTo(1);

        verify(fairCancelMapper).insertFairCanceledHistory(RESERVATION_ID, "CONFIRMED", REASON, null, null, NOW);
        // 사전예약이므로 잡아둔 정원을 돌려준다.
        verify(capacityMapper).release(FAIR_ID, VISIT_DATE);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<SaveNotificationDto.Request> captor =
                ArgumentCaptor.forClass(SaveNotificationDto.Request.class);
        verify(notificationService).save(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().type()).isEqualTo(NotificationType.RESERVATION_CANCELED);
    }

    @Test
    @DisplayName("환불이 끝난 유료 예약은 취소하되, 알림은 행사 도메인에 맡기고 보내지 않는다")
    void cancelsRefundedPaidReservationWithoutDuplicateNotification() {
        CanceledFairReservationRow row = row("CONFIRMED", "COMPLETED", "COMPLETED");
        row.setRefundId(99L);
        row.setRefundAmount(10_000L);
        given(timeProvider.now()).willReturn(NOW);
        given(fairCancelMapper.selectLiveReservationsOfCanceledFairsForUpdate(BATCH)).willReturn(List.of(row));
        // 환불이 끝난 건이므로 갱신 시점에도 환불 완료를 다시 확인하라고(true) 넘긴다.
        given(fairCancelMapper.cancelByFairCancellation(RESERVATION_ID, "CONFIRMED", REASON, true, NOW))
                .willReturn(1);

        assertThat(service.cancelReservationsOfCanceledFairs(BATCH)).isEqualTo(1);

        verify(fairCancelMapper).insertFairCanceledHistory(RESERVATION_ID, "CONFIRMED", REASON, 99L, 10_000L, NOW);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("결제는 끝났는데 환불이 아직이면 이번 회차는 건너뛴다")
    void skipsPaidReservationUntilRefundCompletes() {
        given(timeProvider.now()).willReturn(NOW);
        given(fairCancelMapper.selectLiveReservationsOfCanceledFairsForUpdate(BATCH))
                .willReturn(List.of(row("CONFIRMED", "COMPLETED", null)));

        assertThat(service.cancelReservationsOfCanceledFairs(BATCH)).isZero();

        verify(fairCancelMapper, never()).cancelByFairCancellation(anyLong(), anyString(), anyString(), anyBoolean(), any());
        verifyNoInteractions(capacityMapper, notificationService, eventPublisher);
    }

    @Test
    @DisplayName("환불이 거절된 결제도 취소하지 않는다(돈을 안 돌려준 채 취소로 보이면 안 된다)")
    void skipsWhenRefundWasRejected() {
        given(timeProvider.now()).willReturn(NOW);
        given(fairCancelMapper.selectLiveReservationsOfCanceledFairsForUpdate(BATCH))
                .willReturn(List.of(row("CONFIRMED", "COMPLETED", "REJECTED")));

        assertThat(service.cancelReservationsOfCanceledFairs(BATCH)).isZero();

        verify(fairCancelMapper, never()).cancelByFairCancellation(anyLong(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    @DisplayName("결제가 진행 중인 예약은 건너뛴다(취소된 예약에 돈이 들어오면 안 된다)")
    void skipsWhileMoneyIsInFlight() {
        given(timeProvider.now()).willReturn(NOW);
        given(fairCancelMapper.selectLiveReservationsOfCanceledFairsForUpdate(BATCH))
                .willReturn(List.of(row("PENDING_PAYMENT", "PENDING", null)));

        assertThat(service.cancelReservationsOfCanceledFairs(BATCH)).isZero();

        verify(fairCancelMapper, never()).cancelByFairCancellation(anyLong(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    @DisplayName("이미 끝난 결제(취소·만료·실패)가 딸린 예약은 환불 없이 바로 취소한다")
    void cancelsWhenPaymentAlreadySettledWithoutMoney() {
        given(timeProvider.now()).willReturn(NOW);
        given(fairCancelMapper.selectLiveReservationsOfCanceledFairsForUpdate(BATCH))
                .willReturn(List.of(row("PENDING_PAYMENT", "CANCELED", null)));
        given(fairCancelMapper.cancelByFairCancellation(RESERVATION_ID, "PENDING_PAYMENT", REASON, false, NOW))
                .willReturn(1);

        assertThat(service.cancelReservationsOfCanceledFairs(BATCH)).isEqualTo(1);
    }

    @Test
    @DisplayName("그 사이 상태가 바뀌어 갱신이 0건이면 이력도 정원 반납도 하지 않는다")
    void skipsSideEffectsWhenStatusChangedConcurrently() {
        given(timeProvider.now()).willReturn(NOW);
        given(fairCancelMapper.selectLiveReservationsOfCanceledFairsForUpdate(BATCH))
                .willReturn(List.of(row("CONFIRMED", null, null)));
        given(fairCancelMapper.cancelByFairCancellation(RESERVATION_ID, "CONFIRMED", REASON, false, NOW))
                .willReturn(0);

        assertThat(service.cancelReservationsOfCanceledFairs(BATCH)).isZero();

        verify(fairCancelMapper, never()).insertFairCanceledHistory(anyLong(), anyString(), anyString(), any(), any(), any());
        verifyNoInteractions(capacityMapper);
    }

    @Test
    @DisplayName("현장예매는 사전예약이 아니라 현장 정원으로 반납한다")
    void releasesOnsiteCapacityForOnsiteReservation() {
        CanceledFairReservationRow row = row("CONFIRMED", null, null);
        row.setReservationType("ONSITE_DIRECT");
        given(timeProvider.now()).willReturn(NOW);
        given(fairCancelMapper.selectLiveReservationsOfCanceledFairsForUpdate(BATCH)).willReturn(List.of(row));
        given(fairCancelMapper.cancelByFairCancellation(RESERVATION_ID, "CONFIRMED", REASON, false, NOW))
                .willReturn(1);

        assertThat(service.cancelReservationsOfCanceledFairs(BATCH)).isEqualTo(1);

        // 정원은 유형별로 따로 센다(V49). 사전예약 정원을 건드리면 자리가 새어 나간다.
        verify(capacityMapper).releaseOnsite(FAIR_ID, VISIT_DATE);
        verify(capacityMapper, never()).release(FAIR_ID, VISIT_DATE);
    }

    private CanceledFairReservationRow row(String reservationStatus, String paymentStatus, String refundStatus) {
        CanceledFairReservationRow row = new CanceledFairReservationRow();
        row.setReservationId(RESERVATION_ID);
        row.setUserId(USER_ID);
        row.setFairId(FAIR_ID);
        row.setVisitDate(VISIT_DATE);
        row.setReservationType("ADVANCE");
        row.setReservationStatus(reservationStatus);
        row.setPaymentStatus(paymentStatus);
        row.setRefundStatus(refundStatus);
        return row;
    }
}
