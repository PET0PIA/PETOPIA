package com.ms.petopia.api.review.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.review.dto.CreateFairReviewRequest;
import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.dto.UpdateFairReviewRequest;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.api.review.mapper.FairReviewReplyMapper;
import com.ms.petopia.api.review.mapper.FairReviewReportMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FairReviewServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final Long OTHER_USER_ID = 2L;
    private static final Long REVIEW_ID = 100L;

    @Mock
    private FairReviewMapper fairReviewMapper;
    @Mock
    private FairMapper fairMapper;
    @Mock
    private FairReviewReportMapper fairReviewReportMapper;
    @Mock
    private FairReviewReplyMapper fairReviewReplyMapper;

    @InjectMocks
    private FairReviewService fairReviewService;

    // ===== create =====

    @Test
    @DisplayName("전체공개된 행사면 리뷰를 작성할 수 있다")
    void create_전체공개된행사면_작성할수있다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(publishedFair());

        FairReviewResponse response = fairReviewService.create(FAIR_ID, USER_ID, new CreateFairReviewRequest(5, "좋아요"));

        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.content()).isEqualTo("좋아요");
        verify(fairReviewMapper).insert(any());
    }

    @Test
    @DisplayName("전체공개(published_at) 전이면 REVIEW_FAIR_NOT_PUBLISHED를 던지고 저장하지 않는다")
    void create_전체공개전이면_예외를_던진다() {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setPublishedAt(null);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        assertErrorCode(
                () -> fairReviewService.create(FAIR_ID, USER_ID, new CreateFairReviewRequest(5, "좋아요")),
                ErrorCode.REVIEW_FAIR_NOT_PUBLISHED
        );
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("존재하지 않는 행사면 FAIR_NOT_FOUND를 던진다")
    void create_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(
                () -> fairReviewService.create(FAIR_ID, USER_ID, new CreateFairReviewRequest(5, "좋아요")),
                ErrorCode.FAIR_NOT_FOUND
        );
        verify(fairReviewMapper, never()).insert(any());
    }

    @Test
    @DisplayName("평점이 범위 밖이면 REVIEW_INVALID_RATING을 던진다")
    void create_평점범위밖이면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(publishedFair());

        assertErrorCode(
                () -> fairReviewService.create(FAIR_ID, USER_ID, new CreateFairReviewRequest(6, "좋아요")),
                ErrorCode.REVIEW_INVALID_RATING
        );
    }

    @Test
    @DisplayName("내용이 없으면 REVIEW_CONTENT_REQUIRED를 던진다")
    void create_내용없으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(publishedFair());

        assertErrorCode(
                () -> fairReviewService.create(FAIR_ID, USER_ID, new CreateFairReviewRequest(5, "  ")),
                ErrorCode.REVIEW_CONTENT_REQUIRED
        );
    }

    // ===== update =====

    @Test
    @DisplayName("전체공개된 행사의 본인 리뷰면 수정할 수 있다")
    void update_전체공개된행사면_수정할수있다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairMapper.selectById(FAIR_ID)).willReturn(publishedFair());
        given(fairReviewMapper.update(any())).willReturn(1);

        FairReviewResponse response = fairReviewService.update(
                FAIR_ID, REVIEW_ID, USER_ID, new UpdateFairReviewRequest(4, "수정했어요", 0L)
        );

        assertThat(response.rating()).isEqualTo(4);
        assertThat(response.version()).isEqualTo(1L);
    }

    @Test
    @DisplayName("전체공개 전이면 REVIEW_FAIR_NOT_PUBLISHED를 던지고 갱신하지 않는다")
    void update_전체공개전이면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setPublishedAt(null);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        assertErrorCode(
                () -> fairReviewService.update(FAIR_ID, REVIEW_ID, USER_ID, new UpdateFairReviewRequest(4, "수정", 0L)),
                ErrorCode.REVIEW_FAIR_NOT_PUBLISHED
        );
        verify(fairReviewMapper, never()).update(any());
    }

    @Test
    @DisplayName("본인 리뷰가 아니면 REVIEW_ACCESS_DENIED를 던지고 전체공개 여부는 확인하지 않는다")
    void update_본인리뷰아니면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());

        assertErrorCode(
                () -> fairReviewService.update(FAIR_ID, REVIEW_ID, OTHER_USER_ID, new UpdateFairReviewRequest(4, "수정", 0L)),
                ErrorCode.REVIEW_ACCESS_DENIED
        );
        verify(fairMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("버전이 이미 바뀌었으면(영향 행 0건) REVIEW_VERSION_CONFLICT를 던진다")
    void update_버전이_이미바뀌었으면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairMapper.selectById(FAIR_ID)).willReturn(publishedFair());
        given(fairReviewMapper.update(any())).willReturn(0);

        assertErrorCode(
                () -> fairReviewService.update(FAIR_ID, REVIEW_ID, USER_ID, new UpdateFairReviewRequest(4, "수정", 0L)),
                ErrorCode.REVIEW_VERSION_CONFLICT
        );
    }

    // ===== delete =====

    @Test
    @DisplayName("전체공개된 행사의 본인 리뷰면 삭제할 수 있다(신고·답글도 함께 지운다)")
    void delete_전체공개된행사면_삭제할수있다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairMapper.selectById(FAIR_ID)).willReturn(publishedFair());

        fairReviewService.delete(FAIR_ID, REVIEW_ID, USER_ID);

        verify(fairReviewReportMapper).deleteByReviewId(REVIEW_ID);
        verify(fairReviewReplyMapper).deleteByReviewId(REVIEW_ID);
        verify(fairReviewMapper).deleteById(REVIEW_ID);
    }

    @Test
    @DisplayName("전체공개 전이면 REVIEW_FAIR_NOT_PUBLISHED를 던지고 아무것도 지우지 않는다")
    void delete_전체공개전이면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setPublishedAt(null);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        assertErrorCode(
                () -> fairReviewService.delete(FAIR_ID, REVIEW_ID, USER_ID),
                ErrorCode.REVIEW_FAIR_NOT_PUBLISHED
        );
        verify(fairReviewReportMapper, never()).deleteByReviewId(any());
        verify(fairReviewReplyMapper, never()).deleteByReviewId(any());
        verify(fairReviewMapper, never()).deleteById(any());
    }

    @Test
    @DisplayName("본인 리뷰가 아니면 REVIEW_ACCESS_DENIED를 던지고 아무것도 지우지 않는다")
    void delete_본인리뷰아니면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());

        assertErrorCode(
                () -> fairReviewService.delete(FAIR_ID, REVIEW_ID, OTHER_USER_ID),
                ErrorCode.REVIEW_ACCESS_DENIED
        );
        verify(fairReviewMapper, never()).deleteById(any());
    }

    // ===== fixtures =====

    private Fair publishedFair() {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setPublishedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        return fair;
    }

    private FairReview existingReview() {
        FairReview review = new FairReview();
        review.setReviewId(REVIEW_ID);
        review.setFairId(FAIR_ID);
        review.setUserId(USER_ID);
        review.setRating(5);
        review.setContent("기존 리뷰");
        review.setVerifiedVisit(false);
        review.setCreatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        review.setUpdatedAt(LocalDateTime.of(2026, 8, 1, 0, 0));
        review.setVersion(0L);
        return review;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
