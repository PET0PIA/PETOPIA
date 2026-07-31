package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.CreateReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.dto.ReservationCreationContext;
import com.ms.petopia.api.reservation.dto.ReservationInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long USER_ID = 20L;
    private static final Long RESERVATION_ID = 30L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDate VISIT_DATE = TODAY.plusDays(7);

    @Mock
    private ReservationMapper reservationMapper;

    @Mock
    private ReservationNumberGenerator reservationNumberGenerator;

    @Mock
    private ReservationTimeProvider timeProvider;

    @Mock
    private EntryQrService entryQrService;

    @InjectMocks
    private ReservationService reservationService;

    @BeforeEach
    void setUpTime() {
        org.mockito.Mockito.lenient().when(timeProvider.today()).thenReturn(TODAY);
        org.mockito.Mockito.lenient().when(timeProvider.now()).thenReturn(NOW);
    }

    @Test
    @DisplayName("무료 행사를 예약하면 즉시 확정하고 약관 동의 이력은 남기지 않는다")
    void create_무료행사_즉시확정한다() {
        givenDefaultCreationData(0);
        given(reservationNumberGenerator.generate(any(LocalDate.class))).willReturn("R20260731ABC12345");
        willAnswer(invocation -> {
            ReservationInsertRow row = invocation.getArgument(0);
            row.setReservationId(RESERVATION_ID);
            return 1;
        }).given(reservationMapper).insertReservation(any(ReservationInsertRow.class));

        CreateReservationResponse response = reservationService.create(
                FAIR_ID,
                USER_ID,
                new CreateReservationRequest(VISIT_DATE, null, null)
        );

        assertThat(response.reservationId()).isEqualTo(RESERVATION_ID);
        assertThat(response.reservationType()).isEqualTo("ADVANCE");
        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        assertThat(response.amount()).isZero();
        assertThat(response.paymentRequired()).isFalse();
        assertThat(response.paymentExpiresAt()).isNull();

        ArgumentCaptor<ReservationInsertRow> captor = ArgumentCaptor.forClass(ReservationInsertRow.class);
        verify(reservationMapper).insertReservation(captor.capture());
        ReservationInsertRow inserted = captor.getValue();
        assertThat(inserted.isAgreedTerms()).isFalse();
        assertThat(inserted.getReservationTermsVersion()).isNull();
        assertThat(inserted.getReservationTermsAgreedAt()).isNull();
        assertThat(inserted.getReservedAt()).isNotNull();
        verify(reservationMapper).insertCreatedHistory(RESERVATION_ID, USER_ID, "CONFIRMED");
    }

    @Test
    @DisplayName("유료 행사를 예약하면 결제 대기 상태와 약관 동의 이력을 저장한다")
    void create_유료행사_결제대기상태로만든다() {
        givenDefaultCreationData(15_000);
        given(reservationNumberGenerator.generate(any(LocalDate.class))).willReturn("R20260731ABC12345");
        willAnswer(invocation -> {
            ReservationInsertRow row = invocation.getArgument(0);
            row.setReservationId(RESERVATION_ID);
            return 1;
        }).given(reservationMapper).insertReservation(any(ReservationInsertRow.class));

        CreateReservationResponse response = reservationService.create(
                FAIR_ID,
                USER_ID,
                new CreateReservationRequest(VISIT_DATE, true, "v1.0")
        );

        assertThat(response.reservationStatus()).isEqualTo("PENDING_PAYMENT");
        assertThat(response.amount()).isEqualTo(15_000);
        assertThat(response.paymentRequired()).isTrue();
        assertThat(response.paymentExpiresAt()).isEqualTo(NOW.plusMinutes(10));

        ArgumentCaptor<ReservationInsertRow> captor = ArgumentCaptor.forClass(ReservationInsertRow.class);
        verify(reservationMapper).insertReservation(captor.capture());
        ReservationInsertRow inserted = captor.getValue();
        assertThat(inserted.isAgreedTerms()).isTrue();
        assertThat(inserted.getReservationTermsVersion()).isEqualTo("v1.0");
        assertThat(inserted.getReservationTermsAgreedAt()).isNotNull();
        assertThat(inserted.getReservedAt()).isNull();
        assertThat(inserted.getReservationType()).isEqualTo("ADVANCE");
        assertThat(inserted.getPaymentExpiresAt()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    @DisplayName("유료 예약에서 약관에 동의하지 않으면 예약을 생성하지 않는다")
    void create_유료예약약관미동의_예외를던진다() {
        givenDefaultCreationData(15_000);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, false, "v1.0")
                ),
                ErrorCode.RESERVATION_TERMS_REQUIRED
        );

        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("이미 활성 예약이 있으면 중복 예약으로 거절한다")
    void create_활성예약존재_중복예약예외를던진다() {
        ReservationCreationContext context = reservableContext(0);
        given(reservationMapper.selectCreationContextForUpdate(FAIR_ID, VISIT_DATE)).willReturn(context);
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID)).willReturn(true);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.DUPLICATED_RESERVATION
        );

        verify(reservationMapper, never()).countCapacityOccupyingReservations(FAIR_ID, VISIT_DATE);
        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("날짜 정원이 모두 차면 매진 예외를 던진다")
    void create_정원마감_매진예외를던진다() {
        ReservationCreationContext context = reservableContext(0);
        context.setCapacity(100);
        given(reservationMapper.selectCreationContextForUpdate(FAIR_ID, VISIT_DATE)).willReturn(context);
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID)).willReturn(false);
        given(reservationMapper.countCapacityOccupyingReservations(FAIR_ID, VISIT_DATE)).willReturn(100);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.RESERVATION_SOLD_OUT
        );

        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("행사가 존재하지 않으면 행사 없음 예외를 던진다")
    void create_행사없음_행사없음예외를던진다() {
        given(reservationMapper.selectCreationContextForUpdate(FAIR_ID, VISIT_DATE)).willReturn(null);
        given(reservationMapper.existsFair(FAIR_ID)).willReturn(false);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.RESERVATION_FAIR_NOT_FOUND
        );
    }

    @Test
    @DisplayName("DB 활성 예약 유니크 제약과 충돌하면 중복 예약 예외로 변환한다")
    void create_활성예약유니크충돌_중복예약예외로변환한다() {
        givenDefaultCreationData(0);
        given(reservationNumberGenerator.generate(any(LocalDate.class))).willReturn("R20260731ABC12345");
        given(reservationMapper.insertReservation(any(ReservationInsertRow.class)))
                .willThrow(new DuplicateKeyException(
                        "Duplicate entry for key 'UK_RESERVATION_ACTIVE_USER_FAIR'"
                ));

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.DUPLICATED_RESERVATION
        );

        verify(reservationMapper, never()).insertCreatedHistory(any(), any(), any());
    }

    @Test
    @DisplayName("예약번호 유니크 충돌을 활성 예약 중복으로 잘못 변환하지 않는다")
    void create_예약번호유니크충돌_DB예외를그대로던진다() {
        givenDefaultCreationData(0);
        given(reservationNumberGenerator.generate(any(LocalDate.class))).willReturn("R20260731ABC12345");
        given(reservationMapper.insertReservation(any(ReservationInsertRow.class)))
                .willThrow(new DuplicateKeyException("Duplicate entry for key 'UK_RESERVATION_NO'"));

        assertThatThrownBy(() -> reservationService.create(
                FAIR_ID,
                USER_ID,
                new CreateReservationRequest(VISIT_DATE, null, null)
        )).isInstanceOf(DuplicateKeyException.class);

        verify(reservationMapper, never()).insertCreatedHistory(any(), any(), any());
    }

    private void givenDefaultCreationData(long reservationFee) {
        given(reservationMapper.selectCreationContextForUpdate(FAIR_ID, VISIT_DATE))
                .willReturn(reservableContext(reservationFee));
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID)).willReturn(false);
        given(reservationMapper.countCapacityOccupyingReservations(FAIR_ID, VISIT_DATE)).willReturn(10);
        given(reservationMapper.selectUserSnapshot(USER_ID)).willReturn(activeUser());
    }

    private ReservationCreationContext reservableContext(long reservationFee) {
        ReservationCreationContext context = new ReservationCreationContext();
        context.setFairId(FAIR_ID);
        context.setFairName("펫토피아");
        context.setFairStatus("PREPARING");
        context.setReservationStartDate(TODAY.minusDays(1));
        context.setReservationEndDate(TODAY.plusDays(1));
        context.setReservationFee(reservationFee);
        context.setPublishedAt(NOW.minusDays(1));
        context.setCanceledAt(null);
        context.setFairDateId(100L);
        context.setOperationDate(VISIT_DATE);
        context.setCapacity(100);
        return context;
    }

    private ReservationUserSnapshot activeUser() {
        ReservationUserSnapshot user = new ReservationUserSnapshot();
        user.setUserId(USER_ID);
        user.setNickname("예약자");
        user.setPhone("01012345678");
        user.setEmail("user@example.com");
        user.setRole("USER");
        user.setStatus("ACTIVE");
        return user;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
