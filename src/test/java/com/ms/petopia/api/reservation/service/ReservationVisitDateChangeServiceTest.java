package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationChangeFairDateRow;
import com.ms.petopia.api.reservation.dto.ReservationChangeReservationRow;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateRequest;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateResponse;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import com.ms.petopia.api.reservation.mapper.ReservationChangeMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationVisitDateChangeServiceTest {

    private static final Long RESERVATION_ID = 30L;
    private static final Long FAIR_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final LocalDate CURRENT_DATE = LocalDate.of(2026, 8, 5);
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 6);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 4, 20, 0);

    @Mock
    private ReservationChangeMapper changeMapper;
    @Mock
    private ReservationCapacityMapper capacityMapper;
    @Mock
    private EntryMapper entryMapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReservationPetService reservationPetService;
    @InjectMocks
    private ReservationVisitDateChangeService service;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void changesConfirmedReservationAndUpdatesIssuedQrWindow() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(timeProvider.now()).willReturn(NOW);
        given(timeProvider.today()).willReturn(LocalDate.of(2026, 8, 4));
        given(capacityMapper.occupy(FAIR_ID, TARGET_DATE)).willReturn(1);
        given(changeMapper.updateVisitDate(RESERVATION_ID, TARGET_DATE, NOW)).willReturn(1);

        UpdateReservationVisitDateResponse response = service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        );

        assertThat(response.previousVisitDate()).isEqualTo(CURRENT_DATE);
        assertThat(response.visitDate()).isEqualTo(TARGET_DATE);
        assertThat(response.entryStartTime()).isEqualTo(LocalTime.of(10, 0));
        verify(entryMapper).updateEntryQrAvailability(
                RESERVATION_ID,
                LocalDateTime.of(TARGET_DATE, LocalTime.of(10, 0)),
                LocalDateTime.of(TARGET_DATE, LocalTime.of(18, 0)),
                NOW
        );
        verify(changeMapper).insertVisitDateChangedHistory(
                RESERVATION_ID, CURRENT_DATE, TARGET_DATE, USER_ID, NOW
        );
    }

    @Test
    void replacesPetsWhenPetIdsAreSentWithVisitDateChange() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(timeProvider.now()).willReturn(NOW);
        given(timeProvider.today()).willReturn(LocalDate.of(2026, 8, 4));
        given(capacityMapper.occupy(FAIR_ID, TARGET_DATE)).willReturn(1);
        given(changeMapper.updateVisitDate(RESERVATION_ID, TARGET_DATE, NOW)).willReturn(1);
        given(reservationPetService.isFairPetAllowed(FAIR_ID)).willReturn(true);

        service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE, List.of(7L))
        );

        verify(reservationPetService).replacePets(RESERVATION_ID, USER_ID, true, List.of(7L));
    }

    @Test
    void keepsPetsUntouchedWhenPetIdsAreOmitted() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(timeProvider.now()).willReturn(NOW);
        given(timeProvider.today()).willReturn(LocalDate.of(2026, 8, 4));
        given(capacityMapper.occupy(FAIR_ID, TARGET_DATE)).willReturn(1);
        given(changeMapper.updateVisitDate(RESERVATION_ID, TARGET_DATE, NOW)).willReturn(1);

        // 날짜만 바꾸려는 요청이 동반 정보를 조용히 지워버리면 안 된다.
        service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        );

        verify(reservationPetService, never()).replacePets(any(), any(), anyBoolean(), any());
    }

    @Test
    void replacesPetsWithoutTouchingCapacityWhenVisitDateIsUnchanged() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100)
        ));
        given(timeProvider.now()).willReturn(NOW);
        given(reservationPetService.isFairPetAllowed(FAIR_ID)).willReturn(true);

        // 같은 날짜 + 반려동물만 교체. 정원·QR·변경 이력은 건드리지 않아야 한다.
        service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(CURRENT_DATE, List.of(7L))
        );

        verify(reservationPetService).replacePets(RESERVATION_ID, USER_ID, true, List.of(7L));
        verify(capacityMapper, never()).occupy(any(), any());
        verify(changeMapper, never()).updateVisitDate(any(), any(), any());
        verify(changeMapper, never()).insertVisitDateChangedHistory(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsPendingPaymentReservation() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("PENDING_PAYMENT"));

        assertThatThrownBy(() -> service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        ))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_STATUS_CONFLICT);

        verify(changeMapper, never()).selectFairDatesForUpdate(any(), any());
    }

    @Test
    void rejectsVisitDateChangeRequestFromAnotherUser() {
        ReservationChangeReservationRow reservation = reservation("CONFIRMED");
        reservation.setUserId(99L);
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation);

        assertThatThrownBy(() -> service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        ))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(changeMapper, never()).selectFairDatesForUpdate(any(), any());
    }

    @Test
    void rejectsOnsiteReservation() {
        ReservationChangeReservationRow onsiteReservation = reservation("CONFIRMED");
        onsiteReservation.setReservationType("ONSITE_DIRECT");
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(onsiteReservation);

        assertThatThrownBy(() -> service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        ))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_STATUS_CONFLICT);

        verify(changeMapper, never()).selectFairDatesForUpdate(any(), any());
    }

    @Test
    void rejectsChangeAfterTwelveHourDeadline() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 4, 22, 0, 1));

        assertThatThrownBy(() -> service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        ))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_CHANGE_DEADLINE_EXCEEDED);
    }

    private ReservationChangeReservationRow reservation(String status) {
        ReservationChangeReservationRow row = new ReservationChangeReservationRow();
        row.setReservationId(RESERVATION_ID);
        row.setFairId(FAIR_ID);
        row.setUserId(USER_ID);
        row.setVisitDate(CURRENT_DATE);
        row.setReservationType("ADVANCE");
        row.setStatus(status);
        return row;
    }

    private ReservationChangeFairDateRow fairDate(LocalDate operationDate, int capacity) {
        ReservationChangeFairDateRow row = new ReservationChangeFairDateRow();
        row.setOperationDate(operationDate);
        row.setCapacity(capacity);
        row.setEntryStartTime(LocalTime.of(10, 0));
        row.setEntryEndTime(LocalTime.of(18, 0));
        return row;
    }
}
