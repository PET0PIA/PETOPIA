package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.CreateReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.dto.ReservationCreationContext;
import com.ms.petopia.api.reservation.dto.ReservationInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

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
    private ReservationCapacityMapper capacityMapper;

    @Mock
    private ReservationNumberGenerator reservationNumberGenerator;

    @Mock
    private ReservationTimeProvider timeProvider;

    @Mock
    private EntryQrService entryQrService;
    @Mock
    private ReservationPetService reservationPetService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
    @DisplayName("동반 반려동물을 함께 보내면 예약 행을 만든 뒤 스냅샷을 담는다")
    void create_동반반려동물을_스냅샷으로담는다() {
        givenDefaultCreationData(0);
        given(reservationNumberGenerator.generate(any(LocalDate.class))).willReturn("R20260731ABC12345");
        willAnswer(invocation -> {
            ReservationInsertRow row = invocation.getArgument(0);
            row.setReservationId(RESERVATION_ID);
            return 1;
        }).given(reservationMapper).insertReservation(any(ReservationInsertRow.class));

        reservationService.create(
                FAIR_ID,
                USER_ID,
                new CreateReservationRequest(VISIT_DATE, null, null, List.of(7L, 9L))
        );

        verify(reservationPetService).attachPets(RESERVATION_ID, USER_ID, true, List.of(7L, 9L));
        // 반려동물은 인원이 아니므로 정원은 1건만 점유한다(정책 P6).
        verify(capacityMapper).occupy(FAIR_ID, VISIT_DATE);
    }

    @Test
    @DisplayName("행사가 동반 금지면 그 사실을 그대로 넘겨 서버가 거절하게 한다")
    void create_동반금지행사면_금지여부를_그대로넘긴다() {
        ReservationCreationContext context = reservableContext(0);
        context.setPetAllowed(false);
        givenDefaultCreationData(context);
        given(reservationNumberGenerator.generate(any(LocalDate.class))).willReturn("R20260731ABC12345");
        willAnswer(invocation -> {
            ReservationInsertRow row = invocation.getArgument(0);
            row.setReservationId(RESERVATION_ID);
            return 1;
        }).given(reservationMapper).insertReservation(any(ReservationInsertRow.class));

        reservationService.create(
                FAIR_ID,
                USER_ID,
                new CreateReservationRequest(VISIT_DATE, null, null, List.of(7L))
        );

        verify(reservationPetService).attachPets(RESERVATION_ID, USER_ID, false, List.of(7L));
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
    @DisplayName("중복 검사는 행사·사용자에 방문일까지 포함해 조회한다(다른 방문일은 각각 예약 가능)")
    void create_중복검사에방문일포함_다른방문일은각각예약가능() {
        givenDefaultCreationData(0);
        given(reservationNumberGenerator.generate(any(LocalDate.class))).willReturn("R20260731ABC12345");
        willAnswer(invocation -> {
            ReservationInsertRow row = invocation.getArgument(0);
            row.setReservationId(RESERVATION_ID);
            return 1;
        }).given(reservationMapper).insertReservation(any(ReservationInsertRow.class));

        reservationService.create(
                FAIR_ID,
                USER_ID,
                new CreateReservationRequest(VISIT_DATE, null, null)
        );

        // 정책: 같은 행사라도 방문일이 다르면 각각 예약할 수 있다.
        // 따라서 활성 예약 중복은 (행사, 사용자, 방문일) 조합으로만 판정한다.
        verify(reservationMapper).existsActiveReservation(FAIR_ID, USER_ID, VISIT_DATE);
    }

    @Test
    @DisplayName("이미 활성 예약이 있으면 중복 예약으로 거절한다")
    void create_활성예약존재_중복예약예외를던진다() {
        ReservationCreationContext context = reservableContext(0);
        given(reservationMapper.selectCreationContext(FAIR_ID, VISIT_DATE)).willReturn(context);
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID, VISIT_DATE)).willReturn(true);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.DUPLICATED_RESERVATION
        );

        // 중복 예약은 정원을 건드리기 전에 걸러야 한다. 점유한 뒤 거절하면 아무도 못 사는
        // 좌석이 생긴다(트랜잭션 롤백으로 결국 복구되지만, 순서 자체를 못박아 둔다).
        verify(capacityMapper, never()).occupy(FAIR_ID, VISIT_DATE);
        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("날짜 정원이 모두 차면 매진 예외를 던진다")
    void create_정원마감_매진예외를던진다() {
        ReservationCreationContext context = reservableContext(0);
        given(reservationMapper.selectCreationContext(FAIR_ID, VISIT_DATE)).willReturn(context);
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID, VISIT_DATE)).willReturn(false);
        given(reservationMapper.selectUserSnapshot(USER_ID)).willReturn(activeUser());
        // 정원 판정은 조건부 UPDATE의 영향 행수가 전부다. 0이면 매진.
        given(capacityMapper.occupy(FAIR_ID, VISIT_DATE)).willReturn(0);

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
    @DisplayName("아직 공개되지 않은 행사는 예약을 접수하지 않는다")
    void create_미공개행사_예약접수불가예외를던진다() {
        assertReservationNotOpen(context -> context.setPublishedAt(null));
    }

    @Test
    @DisplayName("취소된 행사는 예약을 접수하지 않는다")
    void create_취소된행사_예약접수불가예외를던진다() {
        assertReservationNotOpen(context -> context.setCanceledAt(NOW.minusHours(1)));
    }

    @Test
    @DisplayName("예약 기간이 설정되지 않은 행사는 예약을 접수하지 않는다")
    void create_예약기간미설정_예약접수불가예외를던진다() {
        assertReservationNotOpen(context -> {
            context.setReservationStartDate(null);
            context.setReservationEndDate(null);
        });
    }

    @Test
    @DisplayName("예약 시작일 전이면 예약을 접수하지 않는다")
    void create_예약시작일전_예약접수불가예외를던진다() {
        assertReservationNotOpen(context -> context.setReservationStartDate(TODAY.plusDays(1)));
    }

    @Test
    @DisplayName("예약 종료일이 지나면 예약을 접수하지 않는다")
    void create_예약종료일후_예약접수불가예외를던진다() {
        assertReservationNotOpen(context -> context.setReservationEndDate(TODAY.minusDays(1)));
    }

    @Test
    @DisplayName("예약 시작일·종료일 당일은 예약 기간에 포함한다")
    void create_예약기간경계일_예약을생성한다() {
        ReservationCreationContext context = reservableContext(0);
        context.setReservationStartDate(TODAY);
        context.setReservationEndDate(TODAY);
        givenDefaultCreationData(context);
        given(reservationNumberGenerator.generate(any(LocalDate.class))).willReturn("R20260731ABC12345");

        CreateReservationResponse response = reservationService.create(
                FAIR_ID,
                USER_ID,
                new CreateReservationRequest(VISIT_DATE, null, null)
        );

        assertThat(response.reservationStatus()).isEqualTo("CONFIRMED");
        verify(reservationMapper).insertReservation(any(ReservationInsertRow.class));
    }

    @Test
    @DisplayName("운영일이 이미 지난 날짜면 방문일을 선택할 수 없다")
    void create_지난운영일_방문일선택불가예외를던진다() {
        ReservationCreationContext context = reservableContext(0);
        context.setOperationDate(TODAY.minusDays(1));
        given(reservationMapper.selectCreationContext(FAIR_ID, VISIT_DATE)).willReturn(context);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.RESERVATION_DATE_NOT_AVAILABLE
        );

        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("당일 사전예약은 생성할 수 없다")
    void create_당일사전예약_방문일선택불가예외를던진다() {
        ReservationCreationContext context = reservableContext(0);
        context.setOperationDate(TODAY);
        given(reservationMapper.selectCreationContext(FAIR_ID, VISIT_DATE)).willReturn(context);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.RESERVATION_DATE_NOT_AVAILABLE
        );

        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("행사는 있지만 해당 방문일 운영일이 없으면 방문일을 선택할 수 없다")
    void create_행사있음운영일없음_방문일선택불가예외를던진다() {
        given(reservationMapper.selectCreationContext(FAIR_ID, VISIT_DATE)).willReturn(null);
        given(reservationMapper.existsFair(FAIR_ID)).willReturn(true);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.RESERVATION_DATE_NOT_AVAILABLE
        );

        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("회원 정보를 찾을 수 없으면 회원 없음 예외를 던진다")
    void create_회원없음_회원없음예외를던진다() {
        givenCreationDataWithUser(null);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.USER_NOT_FOUND
        );

        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("비활성 회원은 예약할 수 없다")
    void create_비활성회원_접근거부예외를던진다() {
        ReservationUserSnapshot user = activeUser();
        user.setStatus("INACTIVE");
        givenCreationDataWithUser(user);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.ACCESS_DENIED
        );

        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("일반 회원이 아닌 계정은 예약할 수 없다")
    void create_일반회원이아닌계정_접근거부예외를던진다() {
        ReservationUserSnapshot user = activeUser();
        user.setRole("EVENT_ADMIN");
        givenCreationDataWithUser(user);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.ACCESS_DENIED
        );

        verify(reservationMapper, never()).insertReservation(any());
    }

    @Test
    @DisplayName("행사가 존재하지 않으면 행사 없음 예외를 던진다")
    void create_행사없음_행사없음예외를던진다() {
        given(reservationMapper.selectCreationContext(FAIR_ID, VISIT_DATE)).willReturn(null);
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
                        "Duplicate entry for key 'UK_RESERVATION_ACTIVE_USER_FAIR_DATE'"
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
    @DisplayName("제약 이름이 최하위 원인에만 있고 소문자여도 중복 예약 예외로 변환한다")
    void create_원인예외의소문자제약이름_중복예약예외로변환한다() {
        givenDefaultCreationData(0);
        given(reservationNumberGenerator.generate(any(LocalDate.class))).willReturn("R20260731ABC12345");
        given(reservationMapper.insertReservation(any(ReservationInsertRow.class)))
                .willThrow(new DuplicateKeyException(
                        "could not execute statement",
                        new SQLException(
                                "Duplicate entry '1-2' for key "
                                        + "'reservations.uk_reservation_active_user_fair_date'"
                        )
                ));

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.DUPLICATED_RESERVATION
        );
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

    /**
     * 예약 접수 기간·공개 여부 검증에서 걸리는지 확인한다.
     *
     * <p>검증이 활성 예약 조회보다 먼저 끝나므로 행사 컨텍스트만 스텁한다.
     */
    private void assertReservationNotOpen(Consumer<ReservationCreationContext> customizer) {
        ReservationCreationContext context = reservableContext(0);
        customizer.accept(context);
        given(reservationMapper.selectCreationContext(FAIR_ID, VISIT_DATE)).willReturn(context);

        assertErrorCode(
                () -> reservationService.create(
                        FAIR_ID,
                        USER_ID,
                        new CreateReservationRequest(VISIT_DATE, null, null)
                ),
                ErrorCode.RESERVATION_NOT_OPEN
        );

        verify(reservationMapper, never()).insertReservation(any());
    }

    private void givenDefaultCreationData(long reservationFee) {
        givenDefaultCreationData(reservableContext(reservationFee));
    }

    /** 회원 검증까지 도달시키기 위해 회원 스냅샷만 바꾼 기본 데이터를 준비한다. */
    private void givenCreationDataWithUser(ReservationUserSnapshot user) {
        given(reservationMapper.selectCreationContext(FAIR_ID, VISIT_DATE))
                .willReturn(reservableContext(0));
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID, VISIT_DATE)).willReturn(false);
        given(reservationMapper.selectUserSnapshot(USER_ID)).willReturn(user);
    }

    private void givenDefaultCreationData(ReservationCreationContext context) {
        given(reservationMapper.selectCreationContext(FAIR_ID, VISIT_DATE))
                .willReturn(context);
        given(reservationMapper.existsActiveReservation(FAIR_ID, USER_ID, VISIT_DATE)).willReturn(false);
        given(reservationMapper.selectUserSnapshot(USER_ID)).willReturn(activeUser());
        // 약관 미동의처럼 정원 점유 전에 걸리는 테스트도 이 헬퍼를 쓰므로 lenient로 둔다.
        org.mockito.Mockito.lenient().when(capacityMapper.occupy(FAIR_ID, VISIT_DATE)).thenReturn(1);
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
        context.setPetAllowed(true);
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
