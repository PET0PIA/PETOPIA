package com.ms.petopia.api.review.service;

import com.ms.petopia.api.review.dto.CreateFairReviewReportRequest;
import com.ms.petopia.api.review.dto.FairReview;
import com.ms.petopia.api.review.dto.FairReviewReportResponse;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.api.review.mapper.FairReviewReportMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FairReviewReportServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long OTHER_FAIR_ID = 20L;
    private static final Long REVIEW_ID = 100L;
    private static final Long REPORTER_USER_ID = 1L;

    @Mock
    private FairReviewReportMapper fairReviewReportMapper;
    @Mock
    private FairReviewMapper fairReviewMapper;

    @InjectMocks
    private FairReviewReportService fairReviewReportService;

    @Test
    @DisplayName("SPAM 등 정해진 사유면 상세 내용 없이 신고할 수 있다")
    void create_고정사유면_상세없이_신고할수있다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());

        FairReviewReportResponse response = fairReviewReportService.create(
                FAIR_ID, REVIEW_ID, REPORTER_USER_ID, new CreateFairReviewReportRequest("SPAM", null)
        );

        assertThat(response.reason()).isEqualTo("SPAM");
        assertThat(response.reasonDetail()).isNull();
        verify(fairReviewReportMapper).insert(any());
    }

    @Test
    @DisplayName("ETC 사유인데 상세 내용이 없으면 REVIEW_REPORT_DETAIL_REQUIRED를 던진다")
    void create_ETC인데_상세없으면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());

        assertErrorCode(
                () -> fairReviewReportService.create(
                        FAIR_ID, REVIEW_ID, REPORTER_USER_ID, new CreateFairReviewReportRequest("ETC", "  ")
                ),
                ErrorCode.REVIEW_REPORT_DETAIL_REQUIRED
        );
        verify(fairReviewReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("ETC 상세 내용이 500자를 초과하면 REVIEW_REPORT_DETAIL_TOO_LONG을 던진다")
    void create_ETC_상세가_길면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        String tooLong = "가".repeat(501);

        assertErrorCode(
                () -> fairReviewReportService.create(
                        FAIR_ID, REVIEW_ID, REPORTER_USER_ID, new CreateFairReviewReportRequest("ETC", tooLong)
                ),
                ErrorCode.REVIEW_REPORT_DETAIL_TOO_LONG
        );
        verify(fairReviewReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("존재하지 않는 리뷰면 REVIEW_NOT_FOUND를 던진다")
    void create_리뷰가_없으면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(null);

        assertErrorCode(
                () -> fairReviewReportService.create(
                        FAIR_ID, REVIEW_ID, REPORTER_USER_ID, new CreateFairReviewReportRequest("SPAM", null)
                ),
                ErrorCode.REVIEW_NOT_FOUND
        );
    }

    @Test
    @DisplayName("리뷰가 다른 행사 소속이면 REVIEW_NOT_FOUND를 던진다")
    void create_다른행사소속이면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());

        assertErrorCode(
                () -> fairReviewReportService.create(
                        OTHER_FAIR_ID, REVIEW_ID, REPORTER_USER_ID, new CreateFairReviewReportRequest("SPAM", null)
                ),
                ErrorCode.REVIEW_NOT_FOUND
        );
    }

    @Test
    @DisplayName("정해진 사유값이 아니면 REVIEW_INVALID_REPORT_REASON을 던진다")
    void create_사유가_잘못되면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());

        assertErrorCode(
                () -> fairReviewReportService.create(
                        FAIR_ID, REVIEW_ID, REPORTER_USER_ID, new CreateFairReviewReportRequest("INVALID", null)
                ),
                ErrorCode.REVIEW_INVALID_REPORT_REASON
        );
    }

    @Test
    @DisplayName("이미 신고한 리뷰면 REVIEW_ALREADY_REPORTED를 던진다")
    void create_이미신고했으면_예외를_던진다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairReviewReportMapper.existsByReviewIdAndReporterUserId(REVIEW_ID, REPORTER_USER_ID)).willReturn(true);

        assertErrorCode(
                () -> fairReviewReportService.create(
                        FAIR_ID, REVIEW_ID, REPORTER_USER_ID, new CreateFairReviewReportRequest("SPAM", null)
                ),
                ErrorCode.REVIEW_ALREADY_REPORTED
        );
        verify(fairReviewReportMapper, never()).insert(any());
    }

    @Test
    @DisplayName("동시 신고로 unique 제약을 위반하면 REVIEW_ALREADY_REPORTED로 변환한다")
    void create_동시신고면_예외로_변환한다() {
        given(fairReviewMapper.selectById(REVIEW_ID)).willReturn(existingReview());
        given(fairReviewReportMapper.existsByReviewIdAndReporterUserId(REVIEW_ID, REPORTER_USER_ID)).willReturn(false);
        willThrow(new DuplicateKeyException("duplicate")).given(fairReviewReportMapper).insert(any());

        assertErrorCode(
                () -> fairReviewReportService.create(
                        FAIR_ID, REVIEW_ID, REPORTER_USER_ID, new CreateFairReviewReportRequest("SPAM", null)
                ),
                ErrorCode.REVIEW_ALREADY_REPORTED
        );
    }

    // ===== fixtures =====

    private FairReview existingReview() {
        FairReview review = new FairReview();
        review.setReviewId(REVIEW_ID);
        review.setFairId(FAIR_ID);
        review.setUserId(2L);
        review.setRating(5);
        review.setContent("기존 리뷰");
        return review;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
