package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateFairApplicationRequest;
import com.ms.petopia.api.fair.dto.CreateFairApplicationResponse;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairApplicationDetailResponse;
import com.ms.petopia.api.fair.dto.FairApplicationSummaryResponse;
import com.ms.petopia.api.fair.dto.FairPublicSummaryResponse;
import com.ms.petopia.api.fair.dto.FairReviewDecision;
import com.ms.petopia.api.fair.dto.FairStatus;
import com.ms.petopia.api.fair.dto.PublishFairResponse;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationRequest;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationResponse;
import com.ms.petopia.api.fair.dto.UpdateFairApplicationRequest;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.auth.service.AdminAccountService;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.global.exception.CommonException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
            "managerName", "managerPhone", "managerEmail"
    );

    @Mock
    private FairMapper fairMapper;

    @Mock
    private FairTimeProvider timeProvider;

    @Mock
    private StorageService storageService;

    @Mock
    private AdminAccountService adminAccountService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private FairService fairService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        org.mockito.Mockito.lenient().when(timeProvider.now()).thenReturn(NOW);
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    // ===== createApplication =====

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
        assertThat(saved.getManagerEmail()).isEqualTo("manager@petopia.example");
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
                "2026 서울 펫페어", "설명", "DOG", "tmp/image/poster.jpg", null,
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
    @DisplayName("예약금이 음수면 INVALID_INPUT_VALUE를 던진다")
    void createApplication_예약금음수면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null,
                null, null, null, null, null, null,
                -1L, null, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.INVALID_INPUT_VALUE);
        verify(fairMapper, never()).insert(any());
    }

    @Test
    @DisplayName("참가업체 모집 종료일이 시작일보다 빠르면 FAIR_INVALID_VENDOR_RECRUIT_PERIOD를 던진다")
    void createApplication_모집기간이_거꾸로면_예외를_던진다() {
        CreateFairApplicationRequest request = new CreateFairApplicationRequest(
                "2026 서울 펫페어", null, null, null, null, null, null, null,
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
                "2026 서울 펫페어", null, null, null, null, null, null, null,
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
                "2026 서울 펫페어", null, null, null, null, null, null, null,
                null, null, null, null, FUTURE_END, FUTURE_START,
                null, null, null,
                "김담당", null, "manager@petopia.example"
        );
        assertErrorCode(() -> fairService.createApplication(USER_ID, request), ErrorCode.FAIR_INVALID_OPERATION_PERIOD);
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
        assertThat(updated.getManagerEmail()).isEqualTo("manager@petopia.example");
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
    @DisplayName("필드를 생략하면(요청에 없으면) 기존 값을 유지한다 - setFields가 비어 매퍼로 전달된다")
    void updateApplication_필드를_생략하면_setFields가_비어있다() {
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
                "이름", null, null, null, null,
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
    @DisplayName("필수 필드(name/managerName/managerEmail)를 명시적으로 비우려 하면 INVALID_INPUT_VALUE를 던진다")
    void updateApplication_필수필드를_명시적으로_비우면_예외를_던진다() {
        UpdateFairApplicationRequest request = new UpdateFairApplicationRequest(
                null, null, null, null, null,
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
                "이름", null, null, null, null,
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
                FAIR_ID, REVIEWER_ID, new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, null)
        );

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.status()).isEqualTo(FairStatus.PAYMENT_PENDING.name());
        assertThat(response.reviewedAt()).isEqualTo(NOW);
        assertThat(response.paymentDueAt()).isEqualTo(NOW.plusDays(7));
        assertThat(response.rejectReason()).isNull();

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).updateReviewResult(captor.capture());
        Fair updated = captor.getValue();
        assertThat(updated.getFairId()).isEqualTo(FAIR_ID);
        assertThat(updated.getReviewedBy()).isEqualTo(REVIEWER_ID);
        assertThat(updated.getReviewedAt()).isEqualTo(NOW);
        assertThat(updated.getStatus()).isEqualTo(FairStatus.PAYMENT_PENDING);
        assertThat(updated.getPaymentDueAt()).isEqualTo(NOW.plusDays(7));

        verify(adminAccountService).issueEventAdminAccount(
                FAIR_ID, USER_ID, "김담당", "manager@petopia.example", null
        );
    }

    @Test
    @DisplayName("RECEIVED 신청서를 사유와 함께 반려하면 REJECTED로 바뀌고 사유를 저장한다")
    void review_반려하면_반려상태와_사유를_설정한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateReviewResult(any())).willReturn(1);

        ReviewFairApplicationResponse response = fairService.review(
                FAIR_ID, REVIEWER_ID, new ReviewFairApplicationRequest(FairReviewDecision.REJECT, "  서류 미비  ")
        );

        assertThat(response.status()).isEqualTo(FairStatus.REJECTED.name());
        assertThat(response.paymentDueAt()).isNull();
        assertThat(response.rejectReason()).isEqualTo("서류 미비");
        verify(adminAccountService, never()).issueEventAdminAccount(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("반려인데 사유가 없으면 FAIR_REJECT_REASON_REQUIRED를 던지고 갱신하지 않는다")
    void review_반려사유없으면_예외를_던진다() {
        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.REJECT, "  ");
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_REJECT_REASON_REQUIRED);
        verify(fairMapper, never()).updateReviewResult(any());
        verify(adminAccountService, never()).issueEventAdminAccount(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("조건부 UPDATE가 영향 행 0건이면(이미 검토됐거나 동시 요청에 밀리면) FAIR_NOT_PENDING_REVIEW를 던지고 계정을 발급하지 않는다")
    void review_조건부갱신이_0건이면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));
        given(fairMapper.updateReviewResult(any())).willReturn(0);

        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, null);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_NOT_PENDING_REVIEW);
        verify(adminAccountService, never()).issueEventAdminAccount(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 신청서를 검토하면 FAIR_NOT_FOUND를 던진다")
    void review_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, null);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_NOT_FOUND);
    }

    @Test
    @DisplayName("검토자 ID나 decision이 없으면 INVALID_INPUT_VALUE를 던진다")
    void review_검토자나결정없으면_예외를_던진다() {
        assertErrorCode(
                () -> fairService.review(FAIR_ID, null, new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, null)),
                ErrorCode.INVALID_INPUT_VALUE
        );
        assertErrorCode(
                () -> fairService.review(FAIR_ID, REVIEWER_ID, new ReviewFairApplicationRequest(null, null)),
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

    // ===== fixtures =====

    private CreateFairApplicationRequest validRequest() {
        return requestWithName("2026 서울 펫페어");
    }

    private CreateFairApplicationRequest requestWithName(String name) {
        return new CreateFairApplicationRequest(
                name, "설명", "DOG", null, null,
                "코엑스", "서울", "INDOOR",
                null, null, null, null, null, null,
                0L, null, null,
                "김담당", "010-0000-0000", "manager@petopia.example"
        );
    }

    private UpdateFairApplicationRequest updateRequest(String name) {
        return new UpdateFairApplicationRequest(
                name, "설명", "DOG", null, null,
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
