package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.CreateFairApplicationRequest;
import com.ms.petopia.api.fair.dto.CreateFairApplicationResponse;
import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairApplicationDetailResponse;
import com.ms.petopia.api.fair.dto.FairReviewDecision;
import com.ms.petopia.api.fair.dto.FairStatus;
import com.ms.petopia.api.fair.dto.PublishFairResponse;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationRequest;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationResponse;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.storage.StorageService;
import com.ms.petopia.global.storage.UploadPolicy;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @Mock
    private FairMapper fairMapper;

    @Mock
    private FairTimeProvider timeProvider;

    @Mock
    private StorageService storageService;

    @InjectMocks
    private FairService fairService;

    @BeforeEach
    void setUpTime() {
        org.mockito.Mockito.lenient().when(timeProvider.now()).thenReturn(NOW);
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

        FairApplicationDetailResponse response = fairService.getApplication(FAIR_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.name()).isEqualTo(fair.getName());
        assertThat(response.status()).isEqualTo("RECEIVED");
        assertThat(response.managerEmail()).isEqualTo(fair.getManagerEmail());
    }

    @Test
    @DisplayName("존재하지 않는 fairId를 조회하면 FAIR_NOT_FOUND를 던진다")
    void getApplication_없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);
        assertErrorCode(() -> fairService.getApplication(FAIR_ID), ErrorCode.FAIR_NOT_FOUND);
    }

    // ===== review =====

    @Test
    @DisplayName("RECEIVED 신청서를 승인하면 PAYMENT_PENDING으로 바뀌고 7일 뒤로 결제 기한을 잡는다")
    void review_승인하면_결제대기상태와_기한을_설정한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));

        ReviewFairApplicationResponse response = fairService.review(
                FAIR_ID, REVIEWER_ID, new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, null)
        );

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.status()).isEqualTo(FairStatus.PAYMENT_PENDING.name());
        assertThat(response.reviewedAt()).isEqualTo(NOW);
        assertThat(response.paymentDueAt()).isEqualTo(NOW.plusDays(7));
        assertThat(response.rejectReason()).isNull();

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).update(captor.capture());
        Fair updated = captor.getValue();
        assertThat(updated.getFairId()).isEqualTo(FAIR_ID);
        assertThat(updated.getReviewedBy()).isEqualTo(REVIEWER_ID);
        assertThat(updated.getReviewedAt()).isEqualTo(NOW);
        assertThat(updated.getStatus()).isEqualTo(FairStatus.PAYMENT_PENDING);
        assertThat(updated.getPaymentDueAt()).isEqualTo(NOW.plusDays(7));
    }

    @Test
    @DisplayName("RECEIVED 신청서를 사유와 함께 반려하면 REJECTED로 바뀌고 사유를 저장한다")
    void review_반려하면_반려상태와_사유를_설정한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.RECEIVED));

        ReviewFairApplicationResponse response = fairService.review(
                FAIR_ID, REVIEWER_ID, new ReviewFairApplicationRequest(FairReviewDecision.REJECT, "  서류 미비  ")
        );

        assertThat(response.status()).isEqualTo(FairStatus.REJECTED.name());
        assertThat(response.paymentDueAt()).isNull();
        assertThat(response.rejectReason()).isEqualTo("서류 미비");
    }

    @Test
    @DisplayName("반려인데 사유가 없으면 FAIR_REJECT_REASON_REQUIRED를 던지고 갱신하지 않는다")
    void review_반려사유없으면_예외를_던진다() {
        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.REJECT, "  ");
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_REJECT_REASON_REQUIRED);
        verify(fairMapper, never()).update(any());
    }

    @Test
    @DisplayName("이미 검토된(RECEIVED가 아닌) 신청서는 FAIR_NOT_PENDING_REVIEW를 던진다")
    void review_이미검토된신청서면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));

        ReviewFairApplicationRequest request = new ReviewFairApplicationRequest(FairReviewDecision.APPROVE, null);
        assertErrorCode(() -> fairService.review(FAIR_ID, REVIEWER_ID, request), ErrorCode.FAIR_NOT_PENDING_REVIEW);
        verify(fairMapper, never()).update(any());
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
        verify(fairMapper, never()).update(any());
    }

    // ===== publish =====

    @Test
    @DisplayName("PAYMENT_PENDING 상태의 행사를 공개하면 published_at을 채운다")
    void publish_공개가능상태면_publishedAt을_설정한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(fairWithStatus(FairStatus.PAYMENT_PENDING));

        PublishFairResponse response = fairService.publish(FAIR_ID, REVIEWER_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.status()).isEqualTo(FairStatus.PAYMENT_PENDING.name());
        assertThat(response.publishedAt()).isEqualTo(NOW);

        ArgumentCaptor<Fair> captor = ArgumentCaptor.forClass(Fair.class);
        verify(fairMapper).update(captor.capture());
        Fair updated = captor.getValue();
        assertThat(updated.getFairId()).isEqualTo(FAIR_ID);
        assertThat(updated.getPublishedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("이미 공개된 행사를 다시 공개하면 갱신 없이 최초 공개 일시를 그대로 반환한다")
    void publish_이미공개됐으면_멱등하게_기존값을_반환한다() {
        Fair fair = fairWithStatus(FairStatus.PAYMENT_PENDING);
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
        Fair fair = fairWithStatus(FairStatus.PAYMENT_PENDING);
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
