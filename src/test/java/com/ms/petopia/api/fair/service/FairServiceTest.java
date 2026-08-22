package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateFairApplicationRequest;
import com.ms.petopia.api.fair.dto.CreateFairApplicationResponse;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairApplicationDetailResponse;
import com.ms.petopia.api.fair.dto.FairApplicationSummaryResponse;
import com.ms.petopia.api.fair.dto.FairOpeningFeeSummaryResponse;
import com.ms.petopia.api.fair.dto.FairPublicListItemResponse;
import com.ms.petopia.api.fair.dto.FairPublicSummaryResponse;
import com.ms.petopia.api.fair.dto.FairReviewDecision;
import com.ms.petopia.api.fair.dto.GeoPoint;
import com.ms.petopia.api.fair.dto.FairStatus;
import com.ms.petopia.api.fair.dto.PublicFairListFilter;
import com.ms.petopia.api.fair.dto.PublishFairResponse;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationRequest;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationResponse;
import com.ms.petopia.api.fair.dto.UpdateFairApplicationRequest;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.service.AdminAccountService;
import com.ms.petopia.api.auth.service.MailService;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.global.exception.CommonException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.test.util.ReflectionTestUtils;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class FairServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long FAIR_ID = 10L;
    private static final Long REVIEWER_ID = 99L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);
    private static final LocalDate FUTURE_START = LocalDate.of(2026, 9, 1);
    private static final LocalDate FUTURE_END = LocalDate.of(2026, 9, 10);

    // updateRequest()가 채우는 모든 필드가 요청에 있었다고 가정할 때(=요약/상세 폼을 통째로
    // 다시 제출하는 실제 FE 흐름과 동일) presentFields로 넘길 값. ALL_UPDATE_SET_FIELDS는
    // FairService#resolveUpdateSetFields를 거친 뒤의 값(posterImageObjectKey -> posterImageUrl)이다.
    private static final Set<String> ALL_UPDATE_FIELDS = Set.of(
            "name", "description", "category", "posterImageObjectKey", "noticeText",
            "placeName", "address", "indoorOutdoor",
            "vendorRecruitStartDate", "vendorRecruitEndDate",
            "reservationStartDate", "reservationEndDate",
            "operationStartDate", "operationEndDate",
            "reservationFee", "reservationCancelDeadlineHours", "reservationChangeDeadlineHours",
            "managerName", "managerPhone", "managerEmail"
    );
    private static final Set<String> ALL_UPDATE_SET_FIELDS = Set.of(
            "name", "description", "category", "posterImageUrl", "noticeText",
            "placeName", "address", "indoorOutdoor",
            "vendorRecruitStartDate", "vendorRecruitEndDate",
            "reservationStartDate", "reservationEndDate",
            "operationStartDate", "operationEndDate",
            "reservationFee", "reservationCancelDeadlineHours", "reservationChangeDeadlineHours",
            "managerName", "managerPhone"
    );

    @Mock
    private FairMapper fairMapper;

    @Mock
    private AuthMapper authMapper;

    @Mock
    private FairTimeProvider timeProvider;

    @Mock
    private StorageService storageService;

    @Mock
    private AdminAccountService adminAccountService;

    @Mock
    private MailService mailService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private FairAdminAccessGuard fairAdminAccessGuard;

    @Mock
    private KakaoGeocodingClient geocodingClient;

    @InjectMocks
    private FairService fairService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        org.mockito.Mockito.lenient().when(timeProvider.now()).thenReturn(NOW);
        org.mockito.Mockito.lenient().when(authMapper.selectUserById(USER_ID)).thenReturn(
                User.builder().userId(USER_ID).email("user@petopia.example").role("USER").build()
        );
        ReflectionTestUtils.setField(fairService, "frontendUrl", "https://petopia-kappa.vercel.app");
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ===== createApplication =====

    @Test
    @DisplayName("참가업체 계정은 행사 개최를 신청할 수 없다")
    void createApplication_VENDOR이면_신청을_거부한다() {
        given(authMapper.selectUserById(USER_ID)).willReturn(
                User.builder().userId(USER_ID).role("VENDOR").build()
        );

        assertErrorCode(
                () -> fairService.createApplication(USER_ID, validRequest()),
                ErrorCode.FAIR_APPLICATION_VENDOR_NOT_ALLOWED
        );
        verify(fairMapper, never()).insert(any(Fair.class));
    }

    @Test
    @DisplayName("필수값을 채워 신청하면 저장하고 RECEIVED 상태로 응답한다")
    void createApplication_정상신청이면_RECEIVED로_응답한다() {
        willAnswer(invocation -> {
            Fair fair = invocation.getArgument(0);
            fair.setFairId(FAIR_ID);
            return 1;
        }).given(fairMapper).insert(any(Fair.class));

        CreateFairApplicationResponse response = fairService.createApplication(USER_ID, validRequest());

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.name()).isEqualTo("2026 서울 펫페어");
        assertThat(response.status()).isEqualTo(FairStatus.RECEIVED.name());
        assertThat(response.createdAt()).isEqualTo(NOW);

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).insert(captor.capture());
        Fair saved = captor.getValue();
        assertThat(saved.getApplicantUserId()).isEqualTo(USER_ID);
        assertThat(saved.getManagerName()).isEqualTo("김담당");
        assertThat(saved.getManagerEmail()).isEqualTo("user@petopia.example");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.getUpdatedAt()).isEqualTo(NOW);
        assertThat(saved.getStatus()).isNull();
        verify(storageService, never()).confirm(any(), any());
    }

    @Test
    @DisplayName("포스터 이미지 객체 키가 있으면 확정 처리 후 공개 URL을 저장한다")
    void createApplication_포스터이미지있으면_확정후_URL을_저장한다() {
        given(storageService.confirm("tmp/image/poster.jpg", UploadPolicy.IMAGE)).willReturn("uploads/image/poster.jpg");
        given(storageService.toPublicUrl("uploads/image/poster.jpg")).willReturn("https://cdn.petopia.example/uploads/image/poster.jpg");
        willAnswer(invocation -> {
            Fair fair = invocation.getArgument(0);
            fair.setFairId(FAIR_ID);
            return 1;
        }).given(fairMapper).insert(any(Fair.class));

        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", "설명", "DOG", "tmp/image/poster.jpg", null, null,
                "코엑스", "서울", "INDOOR",
                null, null, null, null, null, null,
                0L, null, null,
                "김담당", "010-0000-0000", "manager@petopia.example"
        );

        fairService.createApplication(USER_ID, request);

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).insert(captor.capture());
        assertThat(captor.getValue().getPosterImageUrl()).isEqualTo("https://cdn.petopia.example/uploads/image/poster.jpg");
    }

    @Test
    @DisplayName("주소가 있으면 지오코딩 결과로 좌표를 채워 저장한다")
    void createApplication_주소가있으면_지오코딩결과로_좌표를_채운다() {
        given(geocodingClient.geocode("서울"))
                .willReturn(Optional.of(new GeoPoint(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0))));
        willAnswer(invocation -> {
            Fair fair = invocation.getArgument(0);
            fair.setFairId(FAIR_ID);
            return 1;
        }).given(fairMapper).insert(any(Fair.class));

        fairService.createApplication(USER_ID, validRequest());

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).insert(captor.capture());
        assertThat(captor.getValue().getLatitude()).isEqualByComparingTo(BigDecimal.valueOf(37.5));
        assertThat(captor.getValue().getLongitude()).isEqualByComparingTo(BigDecimal.valueOf(127.0));
    }

    @Test
    @DisplayName("지오코딩이 실패해도(빈 결과) 좌표만 비운 채 신청은 정상 처리된다")
    void createApplication_지오코딩실패해도_신청은_정상처리된다() {
        given(geocodingClient.geocode("서울")).willReturn(Optional.empty());
        willAnswer(invocation -> {
            Fair fair = invocation.getArgument(0);
            fair.setFairId(FAIR_ID);
            return 1;
        }).given(fairMapper).insert(any(Fair.class));

        CreateFairApplicationResponse response = fairService.createApplication(USER_ID, validRequest());

        assertThat(response.status()).isEqualTo(FairStatus.RECEIVED.name());
        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).insert(captor.capture());
        assertThat(captor.getValue().getLatitude()).isNull();
        assertThat(captor.getValue().getLongitude()).isNull();
    }

    @Test
    @DisplayName("userId가 없으면 저장하지 않고 INVALID_INPUT_VALUE를 던진다")
    void createApplication_userId없으면_예외를_던진다() {
        assertErrorCode(() -> fairService.createApplication(null, validRequest()), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).insert(any());
    }

    @Test
    @DisplayName("행사명이 비어 있으면 INVALID_INPUT_VALUE를 던진다")
    void createApplication_행사명없으면_예외를_던진다() {
        CreateFairApplicationRequest request = requestWithName(" ");
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).insert(any());
    }

    @Test
    @DisplayName("반려동물 동반 금지로 신청하면 그 값을 저장한다")
    void createApplication_동반금지면_그값을_저장한다() {
        willAnswer(invocation -> {
            Fair fair = invocation.getArgument(0);
            fair.setFairId(FAIR_ID);
            return 1;
        }).given(fairMapper).insert(any(Fair.class));

        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", "설명", "DOG", null, null, false,
                "코엑스", "서울", "INDOOR",
                null, null, null, null, null, null,
                0L, null, null,
                "김담당", "010-0000-0000", "manager@petopia.example"
        );

        fairService.createApplication(USER_ID, request);

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).insert(captor.capture());
        assertThat(captor.getValue().getPetAllowed()).isFalse();
    }

    @Test
    @DisplayName("동반 여부를 보내지 않으면 null로 저장을 맡겨 DB 기본값(동반 가능)이 적용된다")
    void createApplication_동반여부를_안보내면_null로_맡긴다() {
        willAnswer(invocation -> {
            Fair fair = invocation.getArgument(0);
            fair.setFairId(FAIR_ID);
            return 1;
        }).given(fairMapper).insert(any(Fair.class));

        fairService.createApplication(USER_ID, validRequest());

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).insert(captor.capture());
        assertThat(captor.getValue().getPetAllowed()).isNull();
    }

    @Test
    @DisplayName("예약금이 음수면 INVALID_INPUT_VALUE를 던진다")
    void createApplication_예약금음수면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                -1L, null, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).insert(any());
    }

    @Test
    @DisplayName("취소 가능 기한이 음수면 INVALID_INPUT_VALUE를 던진다")
    void createApplication_취소기한이_음수면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                0L, -1, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).insert(any());
    }

    @Test
    @DisplayName("변경 가능 기한이 음수면 INVALID_INPUT_VALUE를 던진다")
    void createApplication_변경기한이_음수면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                0L, null, -1,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).insert(any());
    }

    /** 0은 정상값이다 - "입장 시작 직전까지 취소·변경 허용"을 뜻한다. */
    @Test
    @DisplayName("취소·변경 기한이 0이면 그대로 저장한다")
    void createApplication_기한이_0이면_저장한다() {
        willAnswer(invocation -> {
            Fair fair = invocation.getArgument(0);
            fair.setFairId(FAIR_ID);
            return 1;
        }).given(fairMapper).insert(any(Fair.class));

        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                0L, 0, 0,
                "김담당", null, "manager@petopia.example"
        );
        fairService.createApplication(USER_ID, request);

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).insert(captor.capture());
        assertThat(captor.getValue().getReservationCancelDeadlineHours()).isZero();
        assertThat(captor.getValue().getReservationChangeDeadlineHours()).isZero();
    }

    @Test
    @DisplayName("참가업체 모집 종료일이 시작일보다 빠르면 FAIR_INVALID_VENDOR_RECRUIT_PERIOD를 던진다")
    void createApplication_모집기간이_거꾸로면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                FUTURE_END, FUTURE_START, null, null, null, null,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.FAIR_INVALID_VENDOR_RECRUIT_PERIOD);
    }

    @Test
    @DisplayName("사전예약 종료일이 시작일보다 빠르면 FAIR_INVALID_RESERVATION_PERIOD를 던진다")
    void createApplication_예약기간이_거꾸로면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                null, null, FUTURE_END, FUTURE_START, null, null,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.FAIR_INVALID_RESERVATION_PERIOD);
    }

    @Test
    @DisplayName("행사 운영 종료일이 시작일보다 빠르면 FAIR_INVALID_OPERATION_PERIOD를 던진다")
    void createApplication_운영기간이_거꾸로면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                null, null, null, null, FUTURE_END, FUTURE_START,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.FAIR_INVALID_OPERATION_PERIOD);
    }

    @Test
    @DisplayName("요청의 담당자 이메일 대신 로그인 계정 이메일을 저장한다")
    void createApplication_로그인계정이메일을_저장한다() {
        willAnswer(invocation -> {
            Fair fair = invocation.getArgument(0);
            fair.setFairId(FAIR_ID);
            return 1;
        }).given(fairMapper).insert(any(Fair.class));

        fairService.createApplication(USER_ID, validRequest());

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).insert(captor.capture());
        assertThat(captor.getValue().getManagerEmail()).isEqualTo("user@petopia.example");
    }

    @Test
    @DisplayName("참가업체 모집 시작일이 오늘보다 이전이면 FAIR_VENDOR_RECRUIT_START_IN_PAST를 던진다")
    void createApplication_모집시작일이_오늘보다이전이면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                NOW.toLocalDate().minusDays(1), FUTURE_END, null, null, null, null,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.FAIR_VENDOR_RECRUIT_START_IN_PAST);
    }

    @Test
    @DisplayName("예약 시작일이 오늘보다 이전이면 FAIR_RESERVATION_START_IN_PAST를 던진다")
    void createApplication_예약시작일이_오늘보다이전이면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                null, null, NOW.toLocalDate().minusDays(1), FUTURE_END, null, null,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.FAIR_RESERVATION_START_IN_PAST);
    }

    @Test
    @DisplayName("행사 운영 시작일이 오늘보다 이전이면 FAIR_OPERATION_START_IN_PAST를 던진다")
    void createApplication_운영시작일이_오늘보다이전이면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null, null,
                null, null, null, null, NOW.toLocalDate().minusDays(1), FUTURE_END,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.FAIR_OPERATION_START_IN_PAST);
    }

    // ===== getApplication =====

    @Test
    @DisplayName("존재하는 fairId를 조회하면 상세 응답으로 매핑한다")
    void getApplication_존재하면_상세로_매핑한다() {
        Fair fair = fairWithStatus(FairStatus.RECEIVED);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        FairApplicationDetailResponse response = fairService.getApplication(FAIR_ID, REVIEWER_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.name()).isEqualTo(fair.getName());
        assertThat(response.status()).isEqualTo("RECEIVED");
        assertThat(response.managerEmail()).isEqualTo(fair.getManagerEmail());
        assertThat(response.publishedAt()).isNull();
    }

    @Test
    @DisplayName("공개된 행사는 publishedAt이 그대로 매핑된다 (관리자 화면이 공개 여부를 판단하는 값)")
    void getApplication_공개된행사는_publishedAt이_매핑된다() {
        Fair fair = fairWithStatus(FairStatus.PREPARING);
        fair.setPublishedAt(NOW.minusHours(3));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        FairApplicationDetailResponse response = fairService.getApplication(FAIR_ID, REVIEWER_ID);

        assertThat(response.publishedAt()).isEqualTo(NOW.minusHours(3));
    }

    @Test
    @DisplayName("존재하지 않는 fairId를 조회하면 FAIR_NOT_FOUND를 던진다")
    void getApplication_없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);
        assertErrorCode(() -> fairService.getApplication(FAIR_ID, REVIEWER_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("requesterId가 없으면 조회하지 않고 INVALID_INPUT_VALUE를 던진다")
    void getApplication_requesterId없으면_예외를_던진다() {
        assertErrorCode(() -> fairService.getApplication(FAIR_ID, null), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).selectById(any());
    }

    // ===== getPublicSummary =====

    @Test
    @DisplayName("공개된 행사는 PII 없이 요약 정보로 매핑한다")
    void getPublicSummary_공개됐으면_요약으로_매핑한다() {
        Fair fair = fairWithStatus(FairStatus.PREPARING);
        fair.setPlaceName("코엑스");
        fair.setPublishedAt(NOW.minusDays(1));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        FairPublicSummaryResponse response = fairService.getPublicSummary(FAIR_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.name()).isEqualTo(fair.getName());
        assertThat(response.placeName()).isEqualTo("코엑스");
        assertThat(response.status()).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("공개되지 않은 행사는 존재하지 않는 것과 동일하게 FAIR_NOT_FOUND를 던진다")
    void getPublicSummary_공개안됐으면_예외를_던진다() {
        Fair fair = fairWithStatus(FairStatus.PAYMENT_PENDING);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        assertErrorCode(() -> fairService.getPublicSummary(FAIR_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("공개됐더라도 이후 취소됐으면 FAIR_NOT_FOUND를 던진다")
    void getPublicSummary_공개후취소됐으면_예외를_던진다() {
        Fair fair = fairWithStatus(FairStatus.PREPARING);
        fair.setPublishedAt(NOW.minusDays(2));
        fair.setCanceledAt(NOW.minusDays(1));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        assertErrorCode(() -> fairService.getPublicSummary(FAIR_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 fairId를 조회하면 FAIR_NOT_FOUND를 던진다")
    void getPublicSummary_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(() -> fairService.getPublicSummary(FAIR_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    // ===== getApplications (관리자 심사 큐) =====

    @Test
    @DisplayName("status를 주면 그 상태만 걸러 매퍼에 그대로 넘긴다")
    void getApplications_status를_주면_그대로_넘긴다() {
        Fair fair = fairWithStatus(FairStatus.RECEIVED);
        given(fairMapper.selectByStatus(FairStatus.RECEIVED)).willReturn(List.of(fair));

        List<FairApplicationSummaryResponse> response = fairService.getApplications(FairStatus.RECEIVED);

        verify(fairMapper).selectByStatus(FairStatus.RECEIVED);
        assertThat(response).hasSize(1);
        assertThat(response.get(0).fairId()).isEqualTo(FAIR_ID);
        assertThat(response.get(0).status()).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("status가 없으면 null을 그대로 매퍼에 넘겨 전체를 조회한다")
    void getApplications_status없으면_전체를_조회한다() {
        given(fairMapper.selectByStatus(null)).willReturn(List.of());

        List<FairApplicationSummaryResponse> response = fairService.getApplications(null);

        verify(fairMapper).selectByStatus(null);
        assertThat(response).isEmpty();
    }

    // ===== getMyApplications =====

    @Test
    @DisplayName("본인이 낸 신청서를 요약 목록으로 반환한다")
    void getMyApplications_정상조회() {
        Fair fair = fairWithStatus(FairStatus.RECEIVED);
        given(fairMapper.selectByApplicantUserId(USER_ID)).willReturn(List.of(fair));

        List<FairApplicationSummaryResponse> response = fairService.getMyApplications(USER_ID);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).fairId()).isEqualTo(FAIR_ID);
        assertThat(response.get(0).name()).isEqualTo(fair.getName());
        assertThat(response.get(0).status()).isEqualTo("RECEIVED");
    }

    @Test
    @DisplayName("requesterId가 없으면 조회하지 않고 INVALID_INPUT_VALUE를 던진다")
    void getMyApplications_requesterId없으면_예외를_던진다() {
        assertErrorCode(() -> fairService.getMyApplications(null), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).selectByApplicantUserId(any());
    }

    @Test
    @DisplayName("취소 승인된 행사는 status와 별개로 canceledAt이 채워져 내려간다")
    void getMyApplications_취소된행사는_canceledAt이_채워진다() {
        Fair fair = fairWithStatus(FairStatus.PREPARING);
        fair.setCanceledAt(NOW.minusHours(1));
        given(fairMapper.selectByApplicantUserId(USER_ID)).willReturn(List.of(fair));

        List<FairApplicationSummaryResponse> response = fairService.getMyApplications(USER_ID);

        assertThat(response.get(0).status()).isEqualTo("PREPARING");
        assertThat(response.get(0).canceledAt()).isEqualTo(NOW.minusHours(1));
    }

    // ===== getMyApplicationDetail =====

    @Test
    @DisplayName("본인 신청서를 조회하면 상세로 매핑한다")
    void getMyApplicationDetail_본인이면_상세로_매핑한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));

        FairApplicationDetailResponse response = fairService.getMyApplicationDetail(FAIR_ID, USER_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.managerEmail()).isEqualTo("manager@petopia.example");
        assertThat(response.canceledAt()).isNull();
    }

    @Test
    @DisplayName("취소 승인된 행사는 status와 별개로 canceledAt이 채워져 내려간다")
    void getMyApplicationDetail_취소된행사는_canceledAt이_채워진다() {
        Fair fair = fairWithStatus(FairStatus.IN_PROGRESS);
        fair.setCanceledAt(NOW.minusHours(2));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        FairApplicationDetailResponse response = fairService.getMyApplicationDetail(FAIR_ID, USER_ID);

        assertThat(response.status()).isEqualTo("IN_PROGRESS");
        assertThat(response.canceledAt()).isEqualTo(NOW.minusHours(2));
    }

    @Test
    @DisplayName("본인이 아니면 FAIR_APPLICATION_ACCESS_DENIED를 던진다")
    void getMyApplicationDetail_본인아니면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));

        assertErrorCode(
                () -> fairService.getMyApplicationDetail(FAIR_ID, REVIEWER_ID),
                ErrorCode.FAIR_APPLICATION_ACCESS_DENIED
        );
    }

    @Test
    @DisplayName("존재하지 않는 신청서를 조회하면 FAIR_NOT_FOUND를 던진다")
    void getMyApplicationDetail_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(() -> fairService.getMyApplicationDetail(FAIR_ID, USER_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("requesterId가 없으면 조회하지 않고 INVALID_INPUT_VALUE를 던진다")
    void getMyApplicationDetail_requesterId없으면_예외를_던진다() {
        assertErrorCode(() -> fairService.getMyApplicationDetail(FAIR_ID, null), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).selectById(any());
    }

    // ===== getOpeningFeeSummary =====

    @Test
    @DisplayName("담당 관리자가 조회하면 개설비 요약으로 매핑한다")
    void getOpeningFeeSummary_담당관리자면_요약으로_매핑한다() {
        Fair fair = fairWithStatus(FairStatus.PAYMENT_PENDING);
        fair.setOpeningFeeAmount(500_000L);
        fair.setPaymentDueAt(NOW.plusDays(3));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        FairOpeningFeeSummaryResponse response = fairService.getOpeningFeeSummary(FAIR_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.name()).isEqualTo("2026 서울 펫페어");
        assertThat(response.status()).isEqualTo("PAYMENT_PENDING");
        assertThat(response.openingFeeAmount()).isEqualTo(500_000L);
        assertThat(response.paymentDueAt()).isEqualTo(NOW.plusDays(3));
        verify(fairAdminAccessGuard).checkAssigned(FAIR_ID);
    }

    @Test
    @DisplayName("승인 전(개설비 미확정)이면 openingFeeAmount·paymentDueAt이 null로 내려간다")
    void getOpeningFeeSummary_승인전이면_금액과기한이_null이다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));

        FairOpeningFeeSummaryResponse response = fairService.getOpeningFeeSummary(FAIR_ID);

        assertThat(response.status()).isEqualTo("RECEIVED");
        assertThat(response.openingFeeAmount()).isNull();
        assertThat(response.paymentDueAt()).isNull();
    }

    @Test
    @DisplayName("담당 관리자가 아니면 FairAdminAccessGuard가 던지는 예외가 그대로 전파되고 행사는 조회하지 않는다")
    void getOpeningFeeSummary_담당관리자아니면_예외를_던진다() {
        // 접근 검증을 행사 조회보다 먼저 하므로(ID 존재 여부가 새어나가지 않게) 여기서
        // 예외가 나면 fairMapper.selectById는 아예 호출되지 않는다 - 그래서 그 스텁은 안 둔다.
        willAnswer(invocation -> {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }).given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertErrorCode(
                () -> fairService.getOpeningFeeSummary(FAIR_ID),
                ErrorCode.ACCESS_DENIED
        );
        verify(fairMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("접근 검증을 통과했지만 존재하지 않는 행사면 FAIR_NOT_FOUND를 던진다")
    void getOpeningFeeSummary_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(() -> fairService.getOpeningFeeSummary(FAIR_ID), ErrorCode.FAIR_NOT_FOUND);
        verify(fairAdminAccessGuard).checkAssigned(FAIR_ID);
    }

    // ===== updateApplication =====

    @Test
    @DisplayName("RECEIVED 상태에서 본인이 수정하면 내용을 갱신하고 상세 응답을 반환한다")
    void updateApplication_RECEIVED상태에서_본인이수정하면_갱신한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateApplication(any(), any())).willReturn(1);

        UpdateFairApplicationRequest request = updateRequest("2026 서울 펫페어(수정)");
        FairApplicationDetailResponse response = fairService.updateApplication(FAIR_ID, USER_ID, request, ALL_UPDATE_FIELDS);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).updateApplication(captor.capture(), eq(ALL_UPDATE_SET_FIELDS));
        Fair updated = captor.getValue();
        assertThat(updated.getFairId()).isEqualTo(FAIR_ID);
        assertThat(updated.getName()).isEqualTo("2026 서울 펫페어(수정)");
        // managerEmail은 수정 불가 필드라 UPDATE 객체/SET 절에 포함되지 않는다.
        assertThat(updated.getManagerEmail()).isNull();
    }

    @Test
    @DisplayName("REJECTED 상태에서 본인이 수정해도 재제출로 처리한다")
    void updateApplication_REJECTED상태에서_본인이수정하면_재제출된다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.REJECTED));
        given(fairMapper.updateApplication(any(), any())).willReturn(1);

        fairService.updateApplication(FAIR_ID, USER_ID, updateRequest("2026 서울 펫페어(재제출)"), ALL_UPDATE_FIELDS);

        verify(fairMapper).updateApplication(any(), any());
    }

    @Test
    @DisplayName("주소를 수정하면 재지오코딩해서 좌표를 함께 갱신한다")
    void updateApplication_주소를_수정하면_좌표도_갱신한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateApplication(any(), any())).willReturn(1);
        given(geocodingClient.geocode("부산"))
                .willReturn(Optional.of(new GeoPoint(BigDecimal.valueOf(35.1), BigDecimal.valueOf(129.0))));

        UpdateFairApplicationRequest request = new UpdateFairApplicationRequest(
                null, null, null, null, null, null,
                null, "부산", null,
                null, null, null, null, null, null,
                null, null, null,
                null, null, null
        );

        fairService.updateApplication(FAIR_ID, USER_ID, request, Set.of("address"));

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).updateApplication(captor.capture(), eq(Set.of("address")));
        assertThat(captor.getValue().getLatitude()).isEqualByComparingTo(BigDecimal.valueOf(35.1));
        assertThat(captor.getValue().getLongitude()).isEqualByComparingTo(BigDecimal.valueOf(129.0));
    }

    @Test
    @DisplayName("주소를 수정하지 않으면 재지오코딩을 호출하지 않는다")
    void updateApplication_주소를_수정하지않으면_지오코딩을_호출하지않는다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateApplication(any(), any())).willReturn(1);

        UpdateFairApplicationRequest request = new UpdateFairApplicationRequest(
                "이름만변경", null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null,
                null, null, null
        );

        fairService.updateApplication(FAIR_ID, USER_ID, request, Set.of("name"));

        verify(geocodingClient, never()).geocode(any());
    }

    @Test
    @DisplayName("address 필드가 포함됐지만 값이 그대로면 재지오코딩하지 않고 기존 좌표를 유지한다")
    void updateApplication_주소값이그대로면_기존좌표를_유지한다() {
        // updateRequest()의 address는 항상 "서울" - 폼 전체를 재제출해도(ALL_UPDATE_FIELDS)
        // 실제 주소 값 자체는 안 바뀐, 실무에서 흔한 상황을 재현한다.
        Fair existing = fairWithStatus(FairStatus.RECEIVED);
        existing.setAddress("서울");
        existing.setLatitude(BigDecimal.valueOf(37.5));
        existing.setLongitude(BigDecimal.valueOf(127.0));
        given(fairMapper.selectById(FAIR_ID)).willReturn(existing);
        given(fairMapper.updateApplication(any(), any())).willReturn(1);

        fairService.updateApplication(FAIR_ID, USER_ID, updateRequest("이름만변경"), ALL_UPDATE_FIELDS);

        verify(geocodingClient, never()).geocode(any());
        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).updateApplication(captor.capture(), any());
        assertThat(captor.getValue().getLatitude()).isEqualByComparingTo(BigDecimal.valueOf(37.5));
        assertThat(captor.getValue().getLongitude()).isEqualByComparingTo(BigDecimal.valueOf(127.0));
    }

    @Test
    @DisplayName("본인이 신청한 행사가 아니면 FAIR_APPLICATION_ACCESS_DENIED를 던진다")
    void updateApplication_본인아니면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));

        assertErrorCode(
                () -> fairService.updateApplication(FAIR_ID, REVIEWER_ID, updateRequest("이름변경"), ALL_UPDATE_FIELDS),
                ErrorCode.FAIR_APPLICATION_ACCESS_DENIED
        );
        verify(fairMapper, never()).updateApplication(any(), any());
    }

    @Test
    @DisplayName("조건부 UPDATE가 영향 행 0건이면(RECEIVED/REJECTED가 아니면) FAIR_APPLICATION_NOT_EDITABLE를 던진다")
    void updateApplication_수정불가상태면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));
        given(fairMapper.updateApplication(any(), any())).willReturn(0);

        assertErrorCode(
                () -> fairService.updateApplication(FAIR_ID, USER_ID, updateRequest("이름변경"), ALL_UPDATE_FIELDS),
                ErrorCode.FAIR_APPLICATION_NOT_EDITABLE
        );
    }

    @Test
    @DisplayName("존재하지 않는 신청서를 수정하면 FAIR_NOT_FOUND를 던진다")
    void updateApplication_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(
                () -> fairService.updateApplication(FAIR_ID, USER_ID, updateRequest("이름변경"), ALL_UPDATE_FIELDS),
                ErrorCode.FAIR_NOT_FOUND
        );
    }

    @Test
    @DisplayName("requesterId가 없으면 조회하지 않고 INVALID_INPUT_VALUE를 던진다")
    void updateApplication_requesterId없으면_예외를_던진다() {
        assertErrorCode(
                () -> fairService.updateApplication(FAIR_ID, null, updateRequest("이름변경"), ALL_UPDATE_FIELDS),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(fairMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("보낸 필드가 빈 문자열이면 INVALID_INPUT_VALUE를 던진다")
    void updateApplication_필드가공백이면_예외를_던진다() {
        assertErrorCode(
                () -> fairService.updateApplication(FAIR_ID, USER_ID, updateRequest(" "), ALL_UPDATE_FIELDS),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(fairMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("수정 시 담당자 이메일은 업데이트 대상에서 제외한다")
    void updateApplication_담당자이메일은_수정할수없다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateApplication(any(), any())).willReturn(1);

        // 아무 필드도 요청에 없었던 것처럼(name/managerName/managerEmail도 생략) 호출한다.
        // record 값 자체는 채워져 있어도(테스트 편의상 updateRequest 재사용) presentFields가
        // 비어 있으면 검증도 갱신도 그 필드들을 건드리지 않는다.
        fairService.updateApplication(FAIR_ID, USER_ID, updateRequest("아무이름"), Set.of());

        ArgumentCaptor<Set> setFieldsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(fairMapper).updateApplication(any(), setFieldsCaptor.capture());
        assertThat(setFieldsCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("필드를 명시적으로 비우면(요청에 있고 값이 null) 그 컬럼을 NULL로 지운다")
    void updateApplication_필드를_명시적으로_비우면_NULL로_지운다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateApplication(any(), any())).willReturn(1);

        UpdateFairApplicationRequest request = new UpdateFairApplicationRequest(
                "이름", null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );
        Set<String> presentFields = Set.of("name", "description", "managerName", "managerEmail");

        fairService.updateApplication(FAIR_ID, USER_ID, request, presentFields);

        ArgumentCaptor<Fair> fairCaptor = ArgumentCaptor.forClass(Fair.class);
        ArgumentCaptor<Set> setFieldsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(fairMapper).updateApplication(fairCaptor.capture(), setFieldsCaptor.capture());
        assertThat(fairCaptor.getValue().getDescription()).isNull();
        assertThat(setFieldsCaptor.getValue()).contains("description");
    }

    @Test
    @DisplayName("동반 여부를 수정하면 그 값과 필드명을 함께 넘긴다")
    void updateApplication_동반여부를_수정하면_넘긴다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateApplication(any(), any())).willReturn(1);

        UpdateFairApplicationRequest request = new UpdateFairApplicationRequest(
                "이름", null, null, null, null, false,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );

        fairService.updateApplication(FAIR_ID, USER_ID, request, Set.of("name", "petAllowed", "managerName"));

        ArgumentCaptor<Fair> fairCaptor = ArgumentCaptor.forClass(Fair.class);
        ArgumentCaptor<Set> setFieldsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(fairMapper).updateApplication(fairCaptor.capture(), setFieldsCaptor.capture());
        assertThat(fairCaptor.getValue().getPetAllowed()).isFalse();
        assertThat(setFieldsCaptor.getValue()).contains("petAllowed");
    }

    @Test
    @DisplayName("동반 여부를 명시적으로 null로 지우려 하면 INVALID_INPUT_VALUE를 던진다(NOT NULL 컬럼)")
    void updateApplication_동반여부를_null로_지우면_예외를_던진다() {
        UpdateFairApplicationRequest request = new UpdateFairApplicationRequest(
                "이름", null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );

        assertErrorCode(
                () -> fairService.updateApplication(FAIR_ID, USER_ID, request, Set.of("name", "petAllowed", "managerName")),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(fairMapper, never()).updateApplication(any(), any());
    }

    @Test
    @DisplayName("취소·변경 가능 기한을 음수로 수정하려 하면 INVALID_INPUT_VALUE를 던진다")
    void updateApplication_기한이_음수면_예외를_던진다() {
        UpdateFairApplicationRequest request = new UpdateFairApplicationRequest(
                "이름", null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, -1, null,
                "김담당", null, "manager@petopia.example"
        );

        assertErrorCode(
                () -> fairService.updateApplication(
                        FAIR_ID, USER_ID, request, Set.of("name", "reservationCancelDeadlineHours", "managerName")
                ),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(fairMapper, never()).updateApplication(any(), any());
    }

    @Test
    @DisplayName("수정 가능한 필수 필드(name/managerName)를 명시적으로 비우려 하면 INVALID_INPUT_VALUE를 던진다")
    void updateApplication_필수필드를_명시적으로_비우면_예외를_던진다() {
        UpdateFairApplicationRequest request = new UpdateFairApplicationRequest(
                null, null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );

        assertErrorCode(
                () -> fairService.updateApplication(FAIR_ID, USER_ID, request, Set.of("name", "managerName", "managerEmail")),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(fairMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("posterImageObjectKey를 명시적으로 비우면(null) 포스터 이미지를 삭제한다")
    void updateApplication_posterImageObjectKey를_비우면_포스터를_삭제한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateApplication(any(), any())).willReturn(1);

        UpdateFairApplicationRequest request = new UpdateFairApplicationRequest(
                "이름", null, null, null, null, null,
                null, null, null,
                null, null, null, null, null, null,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );
        Set<String> presentFields = Set.of("name", "posterImageObjectKey", "managerName", "managerEmail");

        fairService.updateApplication(FAIR_ID, USER_ID, request, presentFields);

        ArgumentCaptor<Fair> fairCaptor = ArgumentCaptor.forClass(Fair.class);
        ArgumentCaptor<Set> setFieldsCaptor = ArgumentCaptor.forClass(Set.class);
        verify(fairMapper).updateApplication(fairCaptor.capture(), setFieldsCaptor.capture());
        assertThat(fairCaptor.getValue().getPosterImageUrl()).isNull();
        assertThat(setFieldsCaptor.getValue()).contains("posterImageUrl");
        verify(storageService, never()).confirm(any(), any());
    }

    // ===== review =====

    @Test
    @DisplayName("RECEIVED 신청서를 승인하면 PAYMENT_PENDING으로 바뀌고 7일 뒤로 결제 기한을 잡는다")
    void review_승인하면_결제대기상태와_기한을_설정한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateReviewResult(any())).willReturn(1);

        ReviewFairApplicationResponse response = fairService.review(
                FAIR_ID, REVIEWER_ID, new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, 500_000L, null, null)
        );

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.status()).isEqualTo(FairStatus.PAYMENT_PENDING.name());
        assertThat(response.reviewedAt()).isEqualTo(NOW);
        assertThat(response.openingFeeAmount()).isEqualTo(500_000L);
        assertThat(response.paymentDueAt()).isEqualTo(NOW.plusDays(7));
        assertThat(response.rejectReason()).isNull();

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).updateReviewResult(captor.capture());
        Fair updated = captor.getValue();
        assertThat(updated.getFairId()).isEqualTo(FAIR_ID);
        assertThat(updated.getReviewedBy()).isEqualTo(REVIEWER_ID);
        assertThat(updated.getReviewedAt()).isEqualTo(NOW);
        assertThat(updated.getStatus()).isEqualTo(FairStatus.PAYMENT_PENDING);
        assertThat(updated.getOpeningFeeAmount()).isEqualTo(500_000L);
        assertThat(updated.getPaymentDueAt()).isEqualTo(NOW.plusDays(7));

        verify(adminAccountService).assignApplicantAsEventAdmin(FAIR_ID, USER_ID);

        verifyNoInteractions(mailService, notificationService);

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<SaveNotificationDto.Request> notifCaptor =
                ArgumentCaptor.forClass(SaveNotificationDto.Request.class);
        verify(notificationService).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(notifCaptor.getValue().type()).isEqualTo(NotificationType.FAIR_APPLICATION_APPROVED);
        assertThat(notifCaptor.getValue().channels())
                .containsExactly(DeliveryChannel.IN_APP);
        assertThat(notifCaptor.getValue().linkUrl()).isNull();
        assertThat(notifCaptor.getValue().body())
                .isEqualTo("개설비를 2026-08-08까지 결제해 주세요.");
        verify(mailService).sendFairApprovalEmail(
                "user@petopia.example",
                500_000L,
                NOW.plusDays(7),
                "https://petopia-kappa.vercel.app/payments/fair-opening-fee/" + FAIR_ID
        );
    }

    @Test
    @DisplayName("RECEIVED 신청서를 사유와 함께 반려하면 REJECTED로 바뀌고 사유를 저장한다")
    void review_반려하면_반려상태와_사유를_설정한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateReviewResult(any())).willReturn(1);

        ReviewFairApplicationResponse response = fairService.review(
                FAIR_ID, REVIEWER_ID, new ReviewFairApplicationRequest(FairReviewDecision.REJECT, null, "  서류 미비  ", null)
        );

        assertThat(response.status()).isEqualTo(FairStatus.REJECTED.name());
        assertThat(response.paymentDueAt()).isNull();
        assertThat(response.rejectReason()).isEqualTo("서류 미비");
        verify(adminAccountService, never()).assignApplicantAsEventAdmin(any(), any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<SaveNotificationDto.Request> notifCaptor =
                ArgumentCaptor.forClass(SaveNotificationDto.Request.class);
        verify(notificationService).save(notifCaptor.capture());
        assertThat(notifCaptor.getValue().userId()).isEqualTo(USER_ID);
        assertThat(notifCaptor.getValue().type()).isEqualTo(NotificationType.FAIR_APPLICATION_REJECTED);
        assertThat(notifCaptor.getValue().channels()).containsExactly(DeliveryChannel.IN_APP);
        verify(mailService).sendFairRejectionEmail("user@petopia.example", "서류 미비");
    }

    @Test
    @DisplayName("반려인데 사유가 없으면 FAIR_REJECT_REASON_REQUIRED를 던지고 갱신하지 않는다")
    void review_반려사유없으면_예외를_던진다() {
        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.REJECT, null, "  ", null);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_REJECT_REASON_REQUIRED);
        verify(fairMapper, never()).updateReviewResult(any());
        verify(adminAccountService, never()).assignApplicantAsEventAdmin(any(), any());
    }

    @Test
    @DisplayName("승인인데 개설비 금액이 없으면 FAIR_OPENING_FEE_AMOUNT_REQUIRED를 던지고 갱신하지 않는다")
    void review_승인시금액없으면_예외를_던진다() {
        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, null, null, null);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_OPENING_FEE_AMOUNT_REQUIRED);
        verify(fairMapper, never()).updateReviewResult(any());
        verify(adminAccountService, never()).assignApplicantAsEventAdmin(any(), any());
    }

    @Test
    @DisplayName("승인인데 개설비 금액이 0 이하이면 FAIR_OPENING_FEE_AMOUNT_REQUIRED를 던지고 갱신하지 않는다")
    void review_승인시금액이0이하이면_예외를_던진다() {
        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, 0L, null, null);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_OPENING_FEE_AMOUNT_REQUIRED);
        verify(fairMapper, never()).updateReviewResult(any());
        verify(adminAccountService, never()).assignApplicantAsEventAdmin(any(), any());
    }

    @Test
    @DisplayName("승인 시 결제 기한 일수를 지정하면 그 일수로 결제 기한을 설정한다")
    void review_결제기한을_지정하면_해당일수로_설정한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateReviewResult(any())).willReturn(1);

        ReviewFairApplicationResponse response = fairService.review(
                FAIR_ID, REVIEWER_ID, new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, 500_000L, null, 14)
        );

        assertThat(response.paymentDueAt()).isEqualTo(NOW.plusDays(14));
    }

    @Test
    @DisplayName("승인인데 결제 기한 일수가 0 이하이면 FAIR_PAYMENT_DUE_DAYS_INVALID를 던지고 갱신하지 않는다")
    void review_결제기한이_0이하이면_예외를_던진다() {
        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, 500_000L, null, 0);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_PAYMENT_DUE_DAYS_INVALID);
        verify(fairMapper, never()).updateReviewResult(any());
        verify(adminAccountService, never()).assignApplicantAsEventAdmin(any(), any());
    }

    @Test
    @DisplayName("승인인데 결제 기한 일수가 상한(365일)을 초과하면 FAIR_PAYMENT_DUE_DAYS_INVALID를 던지고 갱신하지 않는다")
    void review_결제기한이_상한을_초과하면_예외를_던진다() {
        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, 500_000L, null, 366);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_PAYMENT_DUE_DAYS_INVALID);
        verify(fairMapper, never()).updateReviewResult(any());
        verify(adminAccountService, never()).assignApplicantAsEventAdmin(any(), any());
    }

    @Test
    @DisplayName("조건부 UPDATE가 영향 행 0건이면(이미 검토됐거나 동시 요청에 밀리면) FAIR_NOT_PENDING_REVIEW를 던지고 계정을 발급하지 않는다")
    void review_조건부갱신이_0건이면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateReviewResult(any())).willReturn(0);

        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, 500_000L, null, null);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_NOT_PENDING_REVIEW);
        verify(adminAccountService, never()).assignApplicantAsEventAdmin(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 신청서를 검토하면 FAIR_NOT_FOUND를 던진다")
    void review_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, 500_000L, null, null);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("검토자 ID나 decision이 없으면 INVALID_INPUT_VALUE를 던진다")
    void review_검토자나결정없으면_예외를_던진다() {
        assertErrorCode(
                () -> fairService.review(FAIR_ID, null, new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, 500_000L, null, null)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertErrorCode(
                () -> fairService.review(FAIR_ID, REVIEWER_ID, new ReviewFairApplicationRequest(null, null, null, null)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        verify(fairMapper, never()).updateReviewResult(any());
    }

    // ===== publish =====

    @Test
    @DisplayName("PREPARING 상태의 행사를 공개하면 published_at을 채운다")
    void publish_공개가능상태면_publishedAt을_설정한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PREPARING));

        PublishFairResponse response = fairService.publish(FAIR_ID, REVIEWER_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.status()).isEqualTo(FairStatus.PREPARING.name());
        assertThat(response.publishedAt()).isEqualTo(NOW);

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).update(captor.capture());
        Fair updated = captor.getValue();
        assertThat(updated.getFairId()).isEqualTo(FAIR_ID);
        assertThat(updated.getPublishedAt()).isEqualTo(NOW);
        verify(fairAdminAccessGuard).checkAssigned(FAIR_ID);
    }

    @Test
    @DisplayName("담당 관리자가 아니면 FairAdminAccessGuard가 던지는 예외가 그대로 전파되고 공개하지 않는다")
    void publish_담당관리자가_아니면_예외가_전파된다() {
        willAnswer(invocation -> {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }).given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertErrorCode(() -> fairService.publish(FAIR_ID, REVIEWER_ID), ErrorCode.ACCESS_DENIED);
        verify(fairMapper, never()).selectById(any());
        verify(fairMapper, never()).update(any());
    }

    @Test
    @DisplayName("개설비 결제 대기 중(PAYMENT_PENDING)인 행사는 공개할 수 없어 FAIR_NOT_PUBLISHABLE을 던진다")
    void publish_개설비결제전이면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));

        assertErrorCode(() -> fairService.publish(FAIR_ID, REVIEWER_ID), ErrorCode.FAIR_NOT_PUBLISHABLE);
        verify(fairMapper, never()).update(any());
    }

    @Test
    @DisplayName("이미 공개된 행사를 다시 공개하면 갱신 없이 최초 공개 일시를 그대로 반환한다")
    void publish_이미공개됐으면_멱등하게_기존값을_반환한다() {
        Fair fair = fairWithStatus(FairStatus.PREPARING);
        LocalDateTime firstPublishedAt = NOW.minusDays(1);
        fair.setPublishedAt(firstPublishedAt);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        PublishFairResponse response = fairService.publish(FAIR_ID, REVIEWER_ID);

        assertThat(response.publishedAt()).isEqualTo(firstPublishedAt);
        verify(fairMapper, never()).update(any());
    }

    @Test
    @DisplayName("RECEIVED 상태의 행사는 공개할 수 없어 FAIR_NOT_PUBLISHABLE을 던진다")
    void publish_심사전이면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));

        assertErrorCode(() -> fairService.publish(FAIR_ID, REVIEWER_ID), ErrorCode.FAIR_NOT_PUBLISHABLE);
        verify(fairMapper, never()).update(any());
    }

    @Test
    @DisplayName("취소된 행사는 공개할 수 없어 FAIR_NOT_PUBLISHABLE을 던진다")
    void publish_취소됐으면_예외를_던진다() {
        Fair fair = fairWithStatus(FairStatus.PREPARING);
        fair.setCanceledAt(NOW.minusHours(1));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        assertErrorCode(() -> fairService.publish(FAIR_ID, REVIEWER_ID), ErrorCode.FAIR_NOT_PUBLISHABLE);
        verify(fairMapper, never()).update(any());
    }

    @Test
    @DisplayName("존재하지 않는 행사를 공개하면 FAIR_NOT_FOUND를 던진다")
    void publish_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);
        assertErrorCode(() -> fairService.publish(FAIR_ID, REVIEWER_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("actorId가 없으면 INVALID_INPUT_VALUE를 던진다")
    void publish_actorId없으면_예외를_던진다() {
        assertErrorCode(() -> fairService.publish(FAIR_ID, null), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).selectById(any());
    }

    // ===== listPublicFairs =====

    @Test
    @DisplayName("filter가 없으면 조회하지 않고 INVALID_INPUT_VALUE를 던진다")
    void listPublicFairs_filter없으면_예외를_던진다() {
        assertErrorCode(() -> fairService.listPublicFairs(null), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).selectPublicFairs(any(), any(), any());
    }

    @Test
    @DisplayName("오늘 날짜(timeProvider 기준)와 filter를 그대로 매퍼에 넘기고 결과를 목록 응답으로 매핑한다")
    void listPublicFairs_오늘날짜와_필터로_조회해서_매핑한다() {
        Fair fair = fairWithStatus(FairStatus.PREPARING);
        fair.setCategory("DOG");
        fair.setPlaceName("코엑스");
        fair.setPosterImageUrl("https://cdn.petopia.example/poster.jpg");
        fair.setOperationStartDate(FUTURE_START);
        fair.setOperationEndDate(FUTURE_END);
        given(fairMapper.selectPublicFairs(PublicFairListFilter.UPCOMING, NOW.toLocalDate(), NOW))
                .willReturn(List.of(fair));

        List<FairPublicListItemResponse> response = fairService.listPublicFairs(PublicFairListFilter.UPCOMING);

        verify(fairMapper).selectPublicFairs(PublicFairListFilter.UPCOMING, NOW.toLocalDate(), NOW);
        assertThat(response).hasSize(1);
        FairPublicListItemResponse item = response.get(0);
        assertThat(item.fairId()).isEqualTo(FAIR_ID);
        assertThat(item.name()).isEqualTo(fair.getName());
        assertThat(item.category()).isEqualTo("DOG");
        assertThat(item.posterImageUrl()).isEqualTo("https://cdn.petopia.example/poster.jpg");
        assertThat(item.placeName()).isEqualTo("코엑스");
        assertThat(item.operationStartDate()).isEqualTo(FUTURE_START);
        assertThat(item.operationEndDate()).isEqualTo(FUTURE_END);
        // 목록 카드가 "오픈 예정"과 "진행 중"을 가르는 값이라 이름 그대로 실려야 한다.
        assertThat(item.status()).isEqualTo("PREPARING");
    }

    @Test
    @DisplayName("PAST 필터로 조회하면 매퍼에 PAST를 그대로 넘긴다")
    void listPublicFairs_PAST필터로_조회한다() {
        given(fairMapper.selectPublicFairs(PublicFairListFilter.PAST, NOW.toLocalDate(), NOW))
                .willReturn(List.of());

        List<FairPublicListItemResponse> response = fairService.listPublicFairs(PublicFairListFilter.PAST);

        verify(fairMapper).selectPublicFairs(PublicFairListFilter.PAST, NOW.toLocalDate(), NOW);
        assertThat(response).isEmpty();
    }

    // ===== listRecruitingFairs =====

    @Test
    @DisplayName("오늘 날짜(timeProvider 기준)로 조회해서 목록 응답으로 매핑한다(published_at과 무관)")
    void listRecruitingFairs_오늘날짜로_조회해서_매핑한다() {
        Fair fair = fairWithStatus(FairStatus.PAYMENT_PENDING);
        fair.setCategory("DOG");
        fair.setPlaceName("코엑스");
        fair.setOperationStartDate(FUTURE_START);
        fair.setOperationEndDate(FUTURE_END);
        given(fairMapper.selectRecruitingFairs(NOW.toLocalDate(), NOW)).willReturn(List.of(fair));

        List<FairPublicListItemResponse> response = fairService.listRecruitingFairs();

        verify(fairMapper).selectRecruitingFairs(NOW.toLocalDate(), NOW);
        assertThat(response).hasSize(1);
        FairPublicListItemResponse item = response.get(0);
        assertThat(item.fairId()).isEqualTo(FAIR_ID);
        assertThat(item.category()).isEqualTo("DOG");
        assertThat(item.placeName()).isEqualTo("코엑스");
        assertThat(item.operationStartDate()).isEqualTo(FUTURE_START);
        assertThat(item.operationEndDate()).isEqualTo(FUTURE_END);
    }

    @Test
    @DisplayName("모집중인 행사가 없으면 빈 목록을 반환한다")
    void listRecruitingFairs_없으면_빈목록을_반환한다() {
        given(fairMapper.selectRecruitingFairs(NOW.toLocalDate(), NOW)).willReturn(List.of());

        List<FairPublicListItemResponse> response = fairService.listRecruitingFairs();

        assertThat(response).isEmpty();
    }

    // ===== fixtures =====

    private CreateFairApplicationRequest validRequest() {
        return requestWithName("2026 서울 펫페어");
    }

    private CreateFairApplicationRequest requestWithName(String name) {
        return new CreateFairApplicationRequest(
                name, "설명", "DOG", null, null, null,
                "코엑스", "서울", "INDOOR",
                null, null, null, null, null, null,
                0L, null, null,
                "김담당", "010-0000-0000", "manager@petopia.example"
        );
    }

    private UpdateFairApplicationRequest updateRequest(String name) {
        return new UpdateFairApplicationRequest(
                name, "설명", "DOG", null, null, null,
                "코엑스", "서울", "INDOOR",
                null, null, null, null, null, null,
                0L, null, null,
                "김담당", "010-0000-0000", "manager@petopia.example"
        );
    }

    private Fair fairWithStatus(FairStatus status) {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setApplicantUserId(USER_ID);
        fair.setName("2026 서울 펫페어");
        fair.setManagerName("김담당");
        fair.setManagerEmail("manager@petopia.example");
        fair.setStatus(status);
        fair.setCreatedAt(NOW.minusDays(1));
        fair.setUpdatedAt(NOW.minusDays(1));
        return fair;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
