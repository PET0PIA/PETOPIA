package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationChangeFairDateRow;
import com.ms.petopia.api.reservation.dto.ReservationChangeReservationRow;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateRequest;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateResponse;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.mapper.EntryMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import com.ms.petopia.api.reservation.mapper.ReservationChangeMapper;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
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
    private ReservationMapper reservationMapper;
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
    void rejectsMoveToDateWhereUserAlreadyHasAnActiveReservation() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(timeProvider.now()).willReturn(NOW);
        given(timeProvider.today()).willReturn(LocalDate.of(2026, 8, 4));
        // 옮겨갈 날짜에 결제 대기 예약이 이미 걸려 있는 상황.
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID, TARGET_DATE)).willReturn(true);

        assertThatThrownBy(() -> service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        ))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATED_RESERVATION);

        // 정원은 손대지 않는다. 점유부터 했다면 실패 시 매진으로 잘못 보이고, 롤백에 기대게 된다.
        verify(capacityMapper, never()).occupy(any(), any());
        verify(capacityMapper, never()).release(any(), any());
        verify(changeMapper, never()).updateVisitDate(any(), any(), any());
    }

    @Test
    void translatesActiveReservationUniqueViolationIntoDuplicateError() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(timeProvider.now()).willReturn(NOW);
        given(timeProvider.today()).willReturn(LocalDate.of(2026, 8, 4));
        given(capacityMapper.occupy(FAIR_ID, TARGET_DATE)).willReturn(1);
        // 사전 검사를 통과한 직후 다른 요청이 그 날짜를 채운 경합 상황. 최종 판정은 DB 제약이 한다.
        given(changeMapper.updateVisitDate(RESERVATION_ID, TARGET_DATE, NOW))
                .willThrow(new DuplicateKeyException(
                        "Duplicate entry '20_10_2026-08-06' for key 'UK_RESERVATION_ACTIVE_USER_FAIR_DATE'"
                ));

        // 500 서버 오류가 아니라 409 중복 예약으로 나가야 한다.
        assertThatThrownBy(() -> service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        ))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.DUPLICATED_RESERVATION);
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

    /**
     * 행사에 변경 가능 기한 설정이 없으면 기본 12시간이 적용된다.
     * null을 명시적으로 stub한다 - Mockito는 Integer 반환에 기본값 0을 주므로,
     * stub을 빼면 "설정 없음"이 아니라 "0시간 설정"이 되어 마감이 사라진다.
     */
    @Test
    void rejectsChangeAfterDefaultTwelveHourDeadline() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(changeMapper.selectChangeDeadlineHours(FAIR_ID)).willReturn(null);
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 4, 22, 0, 1));

        assertThatThrownBy(() -> service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        ))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_CHANGE_DEADLINE_EXCEEDED);
    }

    /**
     * 행사가 정한 기한(48시간)이 기본값(12시간)보다 우선한다.
     * 입장은 8/5 10:00이라 48시간 마감은 8/3 10:00 - 8/4 20:00은 이미 지난 시점이다.
     * 기본값 12시간만 보고 있으면(마감 8/4 22:00) 이 요청은 통과해 버린다.
     */
    @Test
    void usesFairConfiguredChangeDeadlineHours() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(changeMapper.selectChangeDeadlineHours(FAIR_ID)).willReturn(48);
        given(timeProvider.now()).willReturn(NOW);

        assertThatThrownBy(() -> service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        ))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_CHANGE_DEADLINE_EXCEEDED);

        verify(capacityMapper, never()).occupy(any(), any());
    }

    /** 행사가 기한을 0으로 두면 입장 시작 전까지는 변경할 수 있다(마감 = 입장 시작 시각). */
    @Test
    void allowsChangeUntilEntryStartWhenFairSetsZeroDeadline() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(changeMapper.selectChangeDeadlineHours(FAIR_ID)).willReturn(0);
        // 기본값 12시간이면 이미 마감된 시점(입장 9시간 전)이지만, 행사가 0으로 뒀으므로 아직 가능하다.
        LocalDateTime justBeforeEntry = LocalDateTime.of(2026, 8, 5, 1, 0);
        given(timeProvider.now()).willReturn(justBeforeEntry);
        given(timeProvider.today()).willReturn(LocalDate.of(2026, 8, 5));
        given(capacityMapper.occupy(FAIR_ID, TARGET_DATE)).willReturn(1);
        given(changeMapper.updateVisitDate(RESERVATION_ID, TARGET_DATE, justBeforeEntry)).willReturn(1);

        UpdateReservationVisitDateResponse response = service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        );

        assertThat(response.visitDate()).isEqualTo(TARGET_DATE);
    }

    /** 행사에 음수가 저장돼 있으면 마감을 계산하지 않고 잘못된 설정으로 거절한다(취소와 같은 처리). */
    @Test
    void rejectsNegativeConfiguredChangeDeadlineHours() {
        given(changeMapper.selectReservationForUpdate(RESERVATION_ID)).willReturn(reservation("CONFIRMED"));
        given(changeMapper.selectFairDatesForUpdate(eq(FAIR_ID), any())).willReturn(List.of(
                fairDate(CURRENT_DATE, 100), fairDate(TARGET_DATE, 100)
        ));
        given(changeMapper.selectChangeDeadlineHours(FAIR_ID)).willReturn(-1);
        given(timeProvider.now()).willReturn(NOW);

        assertThatThrownBy(() -> service.changeVisitDate(
                RESERVATION_ID, USER_ID, new UpdateReservationVisitDateRequest(TARGET_DATE)
        ))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
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
