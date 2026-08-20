package com.ms.petopia.api.review.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.review.dto.BoothFairBusinessRow;
import com.ms.petopia.api.review.dto.BoothFeedback;
import com.ms.petopia.api.review.dto.BoothFeedbackSubmission;
import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewListResponse;
import com.ms.petopia.api.review.dto.FairReviewListRow;
import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.dto.FairReviewSummaryResponse;
import com.ms.petopia.api.review.dto.FeedbackTag;
import com.ms.petopia.api.review.dto.MyReviewStatusResponse;
import com.ms.petopia.api.review.dto.ReviewTagLabelRow;
import com.ms.petopia.api.review.dto.SubmitFairReviewRequest;
import com.ms.petopia.api.review.mapper.BoothFeedbackMapper;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.api.review.mapper.FeedbackTagMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/*
 * FairReviewService 단위 테스트. Mapper는 Mock으로 대체하고, 제출 자격 검증(행사 공개 상태·
 * 방문 인증·중복 제한)과 부스 평가 처리 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FairReviewServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long OTHER_FAIR_ID = 20L;
    private static final Long USER_ID = 1L;
    private static final Long BOOTH_ID = 100L;
    private static final Long BUSINESS_ID = 200L;
    private static final Long REVIEW_ID = 1000L;

    @Mock
    private FairReviewMapper fairReviewMapper;

    @Mock
    private BoothFeedbackMapper boothFeedbackMapper;

    @Mock
    private FeedbackTagMapper feedbackTagMapper;

    @Mock
    private FairMapper fairMapper;

    @InjectMocks
    private FairReviewService fairReviewService;

    // ===== submit: 자격 검증 =====

    @Test
    @DisplayName("존재하지 않는 행사면 FAIR_NOT_FOUND를 던지고 저장하지 않는다")
    void submit_행사없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(() -> fairReviewService.submit(FAIR_ID, USER_ID, request()), ErrorCode.FAIR_NOT_FOUND);
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("아직 전체공개되지 않은 행사면 REVIEW_FAIR_NOT_PUBLISHED를 던진다")
    void submit_미공개행사면_예외를_던진다() {
        Fair fair = fair(null, null);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        assertErrorCode(() -> fairReviewService.submit(FAIR_ID, USER_ID, request()), ErrorCode.REVIEW_FAIR_NOT_PUBLISHED);
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("공개됐지만 취소된 행사면 REVIEW_FAIR_NOT_PUBLISHED를 던진다")
    void submit_취소된행사면_예외를_던진다() {
        Fair fair = fair(LocalDateTime.now().minusDays(10), LocalDateTime.now().minusDays(1));
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        assertErrorCode(() -> fairReviewService.submit(FAIR_ID, USER_ID, request()), ErrorCode.REVIEW_FAIR_NOT_PUBLISHED);
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("이미 리뷰를 작성했으면 REVIEW_ALREADY_EXISTS를 던진다")
    void submit_이미리뷰있으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(publishedFair());
        given(fairReviewMapper.existsByFairIdAndUserId(FAIR_ID, USER_ID)).willReturn(true);

        assertErrorCode(() -> fairReviewService.submit(FAIR_ID, USER_ID, request()), ErrorCode.REVIEW_ALREADY_EXISTS);
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("행사에 실제로 입장한 기록이 없으면 REVIEW_VISIT_REQUIRED를 던진다")
    void submit_행사방문기록없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(publishedFair());
        given(fairReviewMapper.existsByFairIdAndUserId(FAIR_ID, USER_ID)).willReturn(false);
        given(fairReviewMapper.existsEntryRecord(FAIR_ID, USER_ID)).willReturn(false);

        assertErrorCode(() -> fairReviewService.submit(FAIR_ID, USER_ID, request()), ErrorCode.REVIEW_VISIT_REQUIRED);
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("필수값(동반유형)이 없으면 REVIEW_REQUIRED_FIELD_MISSING을 던진다")
    void submit_필수값없으면_예외를_던진다() {
        stubEligible();
        SubmitFairReviewRequest invalid = new SubmitFairReviewRequest(
                null, FairReview.VisitPurpose.SHOPPING, true, List.of(), List.of()
        );

        assertErrorCode(() -> fairReviewService.submit(FAIR_ID, USER_ID, invalid), ErrorCode.REVIEW_REQUIRED_FIELD_MISSING);
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("부스 평가가 3개를 초과하면 BOOTH_FEEDBACK_LIMIT_EXCEEDED를 던진다")
    void submit_부스가3개초과면_예외를_던진다() {
        stubEligible();
        List<BoothFeedbackSubmission> tooMany = List.of(
                new BoothFeedbackSubmission(1L, List.of(), null),
                new BoothFeedbackSubmission(2L, List.of(), null),
                new BoothFeedbackSubmission(3L, List.of(), null),
                new BoothFeedbackSubmission(4L, List.of(), null)
        );

        assertErrorCode(
                () -> fairReviewService.submit(FAIR_ID, USER_ID, request(tooMany)),
                ErrorCode.BOOTH_FEEDBACK_LIMIT_EXCEEDED
        );
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("존재하지 않거나 다른 scope의 행사 태그를 선택하면 FEEDBACK_TAG_NOT_FOUND를 던진다")
    void submit_행사태그가유효하지않으면_예외를_던진다() {
        stubEligible();
        SubmitFairReviewRequest req = new SubmitFairReviewRequest(
                FairReview.CompanionType.ALONE, FairReview.VisitPurpose.SHOPPING, true, List.of(1L, 2L), List.of()
        );
        given(feedbackTagMapper.selectByIdsAndScope(List.of(1L, 2L), FeedbackTag.Scope.FAIR))
                .willReturn(List.of(activeTag(1L, FeedbackTag.Scope.FAIR))); // 2L 누락

        assertErrorCode(() -> fairReviewService.submit(FAIR_ID, USER_ID, req), ErrorCode.FEEDBACK_TAG_NOT_FOUND);
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("비활성화된 행사 태그를 선택하면 FEEDBACK_TAG_INACTIVE를 던진다")
    void submit_행사태그가비활성이면_예외를_던진다() {
        stubEligible();
        SubmitFairReviewRequest req = new SubmitFairReviewRequest(
                FairReview.CompanionType.ALONE, FairReview.VisitPurpose.SHOPPING, true, List.of(1L), List.of()
        );
        FeedbackTag inactive = activeTag(1L, FeedbackTag.Scope.FAIR);
        inactive.setActive(false);
        given(feedbackTagMapper.selectByIdsAndScope(List.of(1L), FeedbackTag.Scope.FAIR)).willReturn(List.of(inactive));

        assertErrorCode(() -> fairReviewService.submit(FAIR_ID, USER_ID, req), ErrorCode.FEEDBACK_TAG_INACTIVE);
        verify(fairReviewMapper, never()).insert(any());
    }

    // ===== submit: 정상 흐름 =====

    @Test
    @DisplayName("정상 제출이면 리뷰를 저장하고 선택한 행사 태그를 함께 저장한다")
    void submit_정상제출이면_리뷰와태그를_저장한다() {
        stubEligible();
        SubmitFairReviewRequest req = new SubmitFairReviewRequest(
                FairReview.CompanionType.WITH_PET, FairReview.VisitPurpose.EXPERIENCE, true, List.of(1L, 2L), List.of()
        );
        given(feedbackTagMapper.selectByIdsAndScope(List.of(1L, 2L), FeedbackTag.Scope.FAIR))
                .willReturn(List.of(activeTag(1L, FeedbackTag.Scope.FAIR), activeTag(2L, FeedbackTag.Scope.FAIR)));

        FairReviewResponse response = fairReviewService.submit(FAIR_ID, USER_ID, req);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.companionType()).isEqualTo(FairReview.CompanionType.WITH_PET);

        ArgumentCaptor<FairReview> captor = ArgumentCaptor.forClass(FairReview.class);
        verify(fairReviewMapper).insert(captor.capture());
        assertThat(captor.getValue().getFairId()).isEqualTo(FAIR_ID);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().isWouldRevisit()).isTrue();

        verify(fairReviewMapper).insertTagSelections(any(), any());
    }

    @Test
    @DisplayName("행사 태그를 하나도 선택하지 않으면 태그 선택 저장을 호출하지 않는다")
    void submit_태그없으면_태그저장을_호출하지않는다() {
        stubEligible();

        fairReviewService.submit(FAIR_ID, USER_ID, request());

        verify(fairReviewMapper, never()).insertTagSelections(any(), any());
    }

    @Test
    @DisplayName("exists 확인 이후 동시 제출로 유니크 제약을 위반하면 REVIEW_ALREADY_EXISTS로 변환한다")
    void submit_동시제출로_유니크제약위반되면_예외로_변환한다() {
        stubEligible();
        willThrow(new DuplicateKeyException("UK_FAIR_REVIEWS_FAIR_USER")).given(fairReviewMapper).insert(any());

        assertErrorCode(() -> fairReviewService.submit(FAIR_ID, USER_ID, request()), ErrorCode.REVIEW_ALREADY_EXISTS);
    }

    // ===== submit: 부스 평가 =====

    @Test
    @DisplayName("부스가 존재하지 않으면 BOOTH_NOT_FOUND를 던진다")
    void submit_부스없으면_예외를_던진다() {
        stubEligible();
        given(boothFeedbackMapper.selectFairAndBusinessByBoothId(BOOTH_ID)).willReturn(null);

        assertErrorCode(
                () -> fairReviewService.submit(FAIR_ID, USER_ID, request(List.of(new BoothFeedbackSubmission(BOOTH_ID, List.of(), null)))),
                ErrorCode.BOOTH_NOT_FOUND
        );
    }

    @Test
    @DisplayName("부스가 다른 행사 소속이면 BOOTH_NOT_IN_FAIR를 던진다")
    void submit_부스가다른행사소속이면_예외를_던진다() {
        stubEligible();
        given(boothFeedbackMapper.selectFairAndBusinessByBoothId(BOOTH_ID)).willReturn(boothRow(OTHER_FAIR_ID));

        assertErrorCode(
                () -> fairReviewService.submit(FAIR_ID, USER_ID, request(List.of(new BoothFeedbackSubmission(BOOTH_ID, List.of(), null)))),
                ErrorCode.BOOTH_NOT_IN_FAIR
        );
    }

    @Test
    @DisplayName("그 부스를 방문한 기록이 없으면 BOOTH_VISIT_REQUIRED를 던진다")
    void submit_부스방문기록없으면_예외를_던진다() {
        stubEligible();
        given(boothFeedbackMapper.selectFairAndBusinessByBoothId(BOOTH_ID)).willReturn(boothRow(FAIR_ID));
        given(boothFeedbackMapper.existsBoothVisit(BOOTH_ID, USER_ID)).willReturn(false);

        assertErrorCode(
                () -> fairReviewService.submit(FAIR_ID, USER_ID, request(List.of(new BoothFeedbackSubmission(BOOTH_ID, List.of(), null)))),
                ErrorCode.BOOTH_VISIT_REQUIRED
        );
    }

    @Test
    @DisplayName("정상적인 부스 평가는 booth_feedbacks에 저장하고 태그 선택도 함께 저장한다")
    void submit_부스평가를_정상저장한다() {
        stubEligible();
        given(boothFeedbackMapper.selectFairAndBusinessByBoothId(BOOTH_ID)).willReturn(boothRow(FAIR_ID));
        given(boothFeedbackMapper.existsBoothVisit(BOOTH_ID, USER_ID)).willReturn(true);
        given(feedbackTagMapper.selectByIdsAndScope(List.of(9L), FeedbackTag.Scope.BOOTH))
                .willReturn(List.of(activeTag(9L, FeedbackTag.Scope.BOOTH)));
        BoothFeedback inserted = new BoothFeedback();
        inserted.setBoothFeedbackId(500L);
        // insert()가 호출되면 review_id처럼 PK가 채워지는 것을 흉내낸다.
        org.mockito.Mockito.doAnswer(invocation -> {
            BoothFeedback arg = invocation.getArgument(0);
            arg.setBoothFeedbackId(500L);
            return 1;
        }).when(boothFeedbackMapper).insert(any());

        fairReviewService.submit(FAIR_ID, USER_ID,
                request(List.of(new BoothFeedbackSubmission(BOOTH_ID, List.of(9L), BoothFeedback.PurchaseBehavior.PURCHASED))));

        ArgumentCaptor<BoothFeedback> captor = ArgumentCaptor.forClass(BoothFeedback.class);
        verify(boothFeedbackMapper).insert(captor.capture());
        assertThat(captor.getValue().getBoothId()).isEqualTo(BOOTH_ID);
        assertThat(captor.getValue().getFairId()).isEqualTo(FAIR_ID);
        assertThat(captor.getValue().getBusinessId()).isEqualTo(BUSINESS_ID);
        assertThat(captor.getValue().getPurchaseBehavior()).isEqualTo(BoothFeedback.PurchaseBehavior.PURCHASED);

        verify(boothFeedbackMapper).insertSelections(any(), any());
    }

    @Test
    @DisplayName("같은 boothId가 중복 제출되면 첫 번째만 반영한다")
    void submit_중복부스id는_한번만_반영한다() {
        stubEligible();
        given(boothFeedbackMapper.selectFairAndBusinessByBoothId(BOOTH_ID)).willReturn(boothRow(FAIR_ID));
        given(boothFeedbackMapper.existsBoothVisit(BOOTH_ID, USER_ID)).willReturn(true);

        List<BoothFeedbackSubmission> duplicated = List.of(
                new BoothFeedbackSubmission(BOOTH_ID, List.of(), null),
                new BoothFeedbackSubmission(BOOTH_ID, List.of(), null)
        );
        fairReviewService.submit(FAIR_ID, USER_ID, request(duplicated));

        verify(boothFeedbackMapper, times(1)).insert(any());
    }

    // ===== checkStatus =====

    @Test
    @DisplayName("작성한 리뷰가 없고 방문 기록도 없으면 alreadyReviewed=false, hasVisited=false를 반환한다")
    void checkStatus_리뷰없고_미방문이면_false를_반환한다() {
        given(fairReviewMapper.selectByFairIdAndUserId(FAIR_ID, USER_ID)).willReturn(null);
        given(fairReviewMapper.existsEntryRecord(FAIR_ID, USER_ID)).willReturn(false);

        MyReviewStatusResponse response = fairReviewService.checkStatus(FAIR_ID, USER_ID);

        assertThat(response.alreadyReviewed()).isFalse();
        assertThat(response.reviewId()).isNull();
        assertThat(response.hasVisited()).isFalse();
    }

    @Test
    @DisplayName("작성한 리뷰는 없지만 방문 기록이 있으면 hasVisited=true를 반환한다")
    void checkStatus_리뷰없고_방문했으면_hasVisited_true를_반환한다() {
        given(fairReviewMapper.selectByFairIdAndUserId(FAIR_ID, USER_ID)).willReturn(null);
        given(fairReviewMapper.existsEntryRecord(FAIR_ID, USER_ID)).willReturn(true);

        MyReviewStatusResponse response = fairReviewService.checkStatus(FAIR_ID, USER_ID);

        assertThat(response.alreadyReviewed()).isFalse();
        assertThat(response.hasVisited()).isTrue();
    }

    @Test
    @DisplayName("작성한 리뷰가 있으면 reviewId·hasVisited와 함께 alreadyReviewed=true를 반환한다")
    void checkStatus_리뷰있으면_true와_reviewId를_반환한다() {
        FairReview review = new FairReview();
        review.setReviewId(REVIEW_ID);
        given(fairReviewMapper.selectByFairIdAndUserId(FAIR_ID, USER_ID)).willReturn(review);
        given(fairReviewMapper.existsEntryRecord(FAIR_ID, USER_ID)).willReturn(true);

        MyReviewStatusResponse response = fairReviewService.checkStatus(FAIR_ID, USER_ID);

        assertThat(response.alreadyReviewed()).isTrue();
        assertThat(response.reviewId()).isEqualTo(REVIEW_ID);
        assertThat(response.hasVisited()).isTrue();
    }

    // ===== 공개 목록/요약 조회 =====

    @Test
    @DisplayName("page가 음수면 INVALID_INPUT_VALUE를 던진다")
    void listPublic_page가음수면_예외를_던진다() {
        assertErrorCode(() -> fairReviewService.listPublic(FAIR_ID, -1, 10), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("size가 0 이하이거나 상한을 초과하면 INVALID_INPUT_VALUE를 던진다")
    void listPublic_size가유효하지않으면_예외를_던진다() {
        assertErrorCode(() -> fairReviewService.listPublic(FAIR_ID, 0, 0), ErrorCode.INVALID_INPUT_VALUE);
        assertErrorCode(() -> fairReviewService.listPublic(FAIR_ID, 0, 51), ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("목록 항목에 review_id 기준으로 묶은 태그 라벨을 붙여서 반환한다")
    void listPublic_태그라벨을_review_id기준으로_묶어서_반환한다() {
        given(fairReviewMapper.countByFairId(FAIR_ID)).willReturn(2L);
        given(fairReviewMapper.selectListByFairId(FAIR_ID, 0L, 10))
                .willReturn(List.of(listRow(1L, "닉네임1"), listRow(2L, "닉네임2")));
        given(fairReviewMapper.selectFairTagLabelsByReviewIds(List.of(1L, 2L)))
                .willReturn(List.of(tagLabelRow(1L, "안내가 친절해요"), tagLabelRow(1L, "대기시간이 짧아요"), tagLabelRow(2L, "주차가 편리해요")));

        FairReviewListResponse response = fairReviewService.listPublic(FAIR_ID, 0, 10);

        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).nickname()).isEqualTo("닉네임1");
        assertThat(response.items().get(0).fairTagLabels()).containsExactly("안내가 친절해요", "대기시간이 짧아요");
        assertThat(response.items().get(1).fairTagLabels()).containsExactly("주차가 편리해요");
        assertThat(response.totalElements()).isEqualTo(2L);
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("목록이 비어있으면 태그 라벨 배치 조회를 호출하지 않는다")
    void listPublic_목록이비어있으면_태그조회를_호출하지않는다() {
        given(fairReviewMapper.countByFairId(FAIR_ID)).willReturn(0L);
        given(fairReviewMapper.selectListByFairId(FAIR_ID, 0L, 10)).willReturn(List.of());

        FairReviewListResponse response = fairReviewService.listPublic(FAIR_ID, 0, 10);

        assertThat(response.items()).isEmpty();
        verify(fairReviewMapper, never()).selectFairTagLabelsByReviewIds(any());
    }

    @Test
    @DisplayName("리뷰가 하나도 없으면 재방문율은 0이고 재방문 집계 쿼리는 호출하지 않는다")
    void getPublicSummary_리뷰없으면_0을_반환한다() {
        given(fairReviewMapper.countByFairId(FAIR_ID)).willReturn(0L);

        FairReviewSummaryResponse response = fairReviewService.getPublicSummary(FAIR_ID);

        assertThat(response.reviewCount()).isZero();
        assertThat(response.revisitRate()).isZero();
        verify(fairReviewMapper, never()).countRevisitByFairId(any());
    }

    @Test
    @DisplayName("재방문 의향 비율을 리뷰 수 대비로 계산한다")
    void getPublicSummary_재방문비율을_계산한다() {
        given(fairReviewMapper.countByFairId(FAIR_ID)).willReturn(10L);
        given(fairReviewMapper.countRevisitByFairId(FAIR_ID)).willReturn(6L);

        FairReviewSummaryResponse response = fairReviewService.getPublicSummary(FAIR_ID);

        assertThat(response.revisitRate()).isEqualTo(0.6);
    }

    // ===== fixtures =====

    private void stubEligible() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(publishedFair());
        given(fairReviewMapper.existsByFairIdAndUserId(FAIR_ID, USER_ID)).willReturn(false);
        given(fairReviewMapper.existsEntryRecord(FAIR_ID, USER_ID)).willReturn(true);
    }

    private Fair publishedFair() {
        return fair(LocalDateTime.now().minusDays(10), null);
    }

    private Fair fair(LocalDateTime publishedAt, LocalDateTime canceledAt) {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setPublishedAt(publishedAt);
        fair.setCanceledAt(canceledAt);
        return fair;
    }

    private SubmitFairReviewRequest request() {
        return request(List.of());
    }

    private SubmitFairReviewRequest request(List<BoothFeedbackSubmission> booths) {
        return new SubmitFairReviewRequest(
                FairReview.CompanionType.ALONE, FairReview.VisitPurpose.SHOPPING, true, List.of(), booths
        );
    }

    private BoothFairBusinessRow boothRow(Long fairId) {
        BoothFairBusinessRow row = new BoothFairBusinessRow();
        row.setBoothId(BOOTH_ID);
        row.setFairId(fairId);
        row.setBusinessId(BUSINESS_ID);
        return row;
    }

    private FairReviewListRow listRow(Long reviewId, String nickname) {
        FairReviewListRow row = new FairReviewListRow();
        row.setReviewId(reviewId);
        row.setNickname(nickname);
        row.setCompanionType(FairReview.CompanionType.ALONE);
        row.setVisitPurpose(FairReview.VisitPurpose.SHOPPING);
        row.setWouldRevisit(true);
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    private ReviewTagLabelRow tagLabelRow(Long reviewId, String label) {
        ReviewTagLabelRow row = new ReviewTagLabelRow();
        row.setReviewId(reviewId);
        row.setLabel(label);
        return row;
    }

    private FeedbackTag activeTag(Long tagId, FeedbackTag.Scope scope) {
        FeedbackTag tag = new FeedbackTag();
        tag.setTagId(tagId);
        tag.setScope(scope);
        tag.setCategory(scope == FeedbackTag.Scope.FAIR ? "GUIDE_OPERATION" : "CONSULTATION");
        tag.setSentiment(FeedbackTag.Sentiment.POSITIVE);
        tag.setLabel("태그" + tagId);
        tag.setActive(true);
        return tag;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
