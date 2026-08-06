package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.CreateOnsiteReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateOnsiteReservationResponse;
import com.ms.petopia.api.reservation.dto.OnsiteReservationCreationContext;
import com.ms.petopia.api.reservation.dto.ReservationInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import com.ms.petopia.api.reservation.mapper.OnsiteReservationMapper;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OnsiteReservationServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final Long RESERVATION_ID = 30L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock
    private OnsiteReservationMapper onsiteReservationMapper;
    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private ReservationNumberGenerator reservationNumberGenerator;
    @Mock
    private ReservationTimeProvider timeProvider;
    @Mock
    private EntryQrService entryQrService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OnsiteReservationService service;

    @Test
    @DisplayName("무료 현장예매는 정원을 조회하지 않고 즉시 확정해 QR을 발급한다")
    void create_freeOnsiteReservation_confirmsWithoutCapacity() {
        givenOpenContext(0);
        givenUserAndNoDuplicate();
        given(reservationNumberGenerator.generate(NOW.toLocalDate())).willReturn("R20260801FREE0001");
        given(entryQrService.issueForReservation(RESERVATION_ID)).willReturn("qr-token");
        assignGeneratedReservationId();

        CreateOnsiteReservationResponse response = service.create(FAIR_ID, USER_ID, null);

        assertThat(response.reservationType()).isEqualTo("ONSITE_DIRECT");
        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.amount()).isZero();
        assertThat(response.paymentExpiresAt()).isNull();
        assertThat(response.entryQrToken()).isEqualTo("qr-token");
        verify(reservationMapper, never()).countCapacityOccupyingReservations(any(), any());

        ArgumentCaptor<ReservationInsertRow> captor = ArgumentCaptor.forClass(ReservationInsertRow.class);
        verify(reservationMapper).insertReservation(captor.capture());
        assertThat(captor.getValue().getReservationType()).isEqualTo("ONSITE_DIRECT");
        assertThat(captor.getValue().getReservedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("유료 현장예매는 생성 당시 가격과 10분 제한시각을 저장한다")
    void create_paidOnsiteReservation_snapshotsPriceAndDeadline() {
        givenOpenContext(12_345);
        givenUserAndNoDuplicate();
        given(reservationNumberGenerator.generate(NOW.toLocalDate())).willReturn("R20260801PAID0001");
        assignGeneratedReservationId();

        CreateOnsiteReservationResponse response = service.create(
                FAIR_ID,
                USER_ID,
                new CreateOnsiteReservationRequest(true, OnsiteReservationService.ONSITE_TERMS_VERSION)
        );

        assertThat(response.reservationStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.amount()).isEqualTo(12_345);
        assertThat(response.paymentExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(response.entryQrToken()).isNull();

        ArgumentCaptor<ReservationInsertRow> captor = ArgumentCaptor.forClass(ReservationInsertRow.class);
        verify(reservationMapper).insertReservation(captor.capture());
        ReservationInsertRow inserted = captor.getValue();
        assertThat(inserted.getReservationAmount()).isEqualTo(12_345);
        assertThat(inserted.getPaymentExpiresAt()).isEqualTo(NOW.plusMinutes(10));
        assertThat(inserted.getReservationTermsVersion())
                .isEqualTo(OnsiteReservationService.ONSITE_TERMS_VERSION);
        verify(entryQrService, never()).issueForReservation(any());
    }

    @Test
    @DisplayName("유료 현장예매는 10분 결제 제한시간이 입장 마감시각에 정확히 끝나면 생성할 수 있다")
    void create_paidOnsiteReservation_allowsPaymentDeadlineAtEntryEnd() {
        LocalDateTime tenMinutesBeforeEntryEnd = LocalDateTime.of(2026, 8, 1, 17, 50);
        givenOpenContextAt(tenMinutesBeforeEntryEnd, 12_345);
        givenUserAndNoDuplicate();
        given(reservationNumberGenerator.generate(tenMinutesBeforeEntryEnd.toLocalDate()))
                .willReturn("R20260801PAID0002");
        assignGeneratedReservationId();

        CreateOnsiteReservationResponse response = service.create(
                FAIR_ID,
                USER_ID,
                new CreateOnsiteReservationRequest(true, OnsiteReservationService.ONSITE_TERMS_VERSION)
        );

        assertThat(response.reservationStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.paymentExpiresAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 18, 0));
    }

    @Test
    @DisplayName("유료 현장예매는 10분 결제 제한시간이 입장 마감시각을 넘으면 생성할 수 없다")
    void create_paidOnsiteReservation_rejectsWhenPaymentDeadlineExceedsEntryEnd() {
        LocalDateTime nineMinutesBeforeEntryEnd = LocalDateTime.of(2026, 8, 1, 17, 51);
        givenOpenContextAt(nineMinutesBeforeEntryEnd, 12_345);

        assertError(
                () -> service.create(FAIR_ID, USER_ID,
                        new CreateOnsiteReservationRequest(true, OnsiteReservationService.ONSITE_TERMS_VERSION)),
                ErrorCode.ONSITE_RESERVATION_CLOSED
        );
        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("입장 마감시각 이후에는 무료 현장예매도 생성할 수 없다")
    void create_freeOnsiteReservation_rejectsAfterEntryEnd() {
        LocalDateTime afterEntryEnd = LocalDateTime.of(2026, 8, 1, 18, 0, 1);
        givenOpenContextAt(afterEntryEnd, 0);

        assertError(() -> service.create(FAIR_ID, USER_ID, null), ErrorCode.ONSITE_RESERVATION_CLOSED);
        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("무료 현장예매는 입장 마감시각에는 생성할 수 있다")
    void create_freeOnsiteReservation_allowsAtEntryEnd() {
        LocalDateTime entryEnd = LocalDateTime.of(2026, 8, 1, 18, 0);
        givenOpenContextAt(entryEnd, 0);
        givenUserAndNoDuplicate();
        given(reservationNumberGenerator.generate(entryEnd.toLocalDate())).willReturn("R20260801FREE0002");
        given(entryQrService.issueForReservation(RESERVATION_ID)).willReturn("qr-token");
        assignGeneratedReservationId();

        CreateOnsiteReservationResponse response = service.create(FAIR_ID, USER_ID, null);

        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.entryQrToken()).isEqualTo("qr-token");
    }

    @Test
    @DisplayName("현장예매가 일시중지되면 신규 예약을 거절한다")
    void create_paused_rejects() {
        OnsiteReservationCreationContext context = context(10_000, "PAUSED");
        given(timeProvider.now()).willReturn(NOW);
        given(onsiteReservationMapper.selectCreationContextForUpdate(FAIR_ID, NOW.toLocalDate()))
                .willReturn(context);

        assertError(
                () -> service.create(FAIR_ID, USER_ID,
                        new CreateOnsiteReservationRequest(true, OnsiteReservationService.ONSITE_TERMS_VERSION)),
                ErrorCode.ONSITE_RESERVATION_PAUSED
        );
        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("유료 현장예매는 서버의 현재 약관 버전과 정확히 일치해야 한다")
    void create_wrongTermsVersion_rejects() {
        givenOpenContext(10_000);
        givenUserAndNoDuplicate();

        assertError(
                () -> service.create(FAIR_ID, USER_ID,
                        new CreateOnsiteReservationRequest(true, "old-version")),
                ErrorCode.RESERVATION_TERMS_REQUIRED
        );
        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("사전예약을 포함한 활성 예약이 있으면 현장예매를 거절한다")
    void create_existingReservation_rejects() {
        givenOpenContext(0);
        given(reservationMapper.selectUserSnapshot(USER_ID)).willReturn(activeUser());
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID)).willReturn(true);

        assertError(() -> service.create(FAIR_ID, USER_ID, null), ErrorCode.DUPLICATED_RESERVATION);
        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("동시 요청이 DB 활성예약 제약과 충돌하면 중복 예약으로 변환한다")
    void create_uniqueConflict_mapsToDuplicateReservation() {
        givenOpenContext(0);
        givenUserAndNoDuplicate();
        given(reservationNumberGenerator.generate(NOW.toLocalDate())).willReturn("R20260801DUPL0001");
        given(reservationMapper.insertReservation(any()))
                .willThrow(new DuplicateKeyException("UK_RESERVATION_ACTIVE_USER_FAIR"));

        assertError(() -> service.create(FAIR_ID, USER_ID, null), ErrorCode.DUPLICATED_RESERVATION);
    }

    private void givenOpenContext(long price) {
        givenOpenContextAt(NOW, price);
    }

    private void givenOpenContextAt(LocalDateTime now, long price) {
        given(timeProvider.now()).willReturn(now);
        given(onsiteReservationMapper.selectCreationContextForUpdate(FAIR_ID, now.toLocalDate()))
                .willReturn(context(price, "OPEN"));
    }

    private void givenUserAndNoDuplicate() {
        given(reservationMapper.selectUserSnapshot(USER_ID)).willReturn(activeUser());
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID)).willReturn(false);
    }

    private void assignGeneratedReservationId() {
        willAnswer(invocation -> {
            ReservationInsertRow row = invocation.getArgument(0);
            row.setReservationId(RESERVATION_ID);
            return 1;
        }).given(reservationMapper).insertReservation(any());
    }

    private OnsiteReservationCreationContext context(long price, String status) {
        OnsiteReservationCreationContext context = new OnsiteReservationCreationContext();
        context.setFairId(FAIR_ID);
        context.setFairName("펫토피아");
        context.setFairStatus("IN_PROGRESS");
        context.setPublishedAt(NOW.minusDays(1));
        context.setFairDateId(100L);
        context.setOperationDate(NOW.toLocalDate());
        context.setEntryStartTime(LocalTime.of(9, 0));
        context.setEntryEndTime(LocalTime.of(18, 0));
        context.setOnsitePrice(price);
        context.setOnsiteSalesStatus(status);
        return context;
    }

    private ReservationUserSnapshot activeUser() {
        ReservationUserSnapshot user = new ReservationUserSnapshot();
        user.setUserId(USER_ID);
        user.setNickname("현장 방문자");
        user.setPhone("01012345678");
        user.setEmail("visitor@example.com");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        return user;
    }

    private void assertError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(expected);
    }
}
