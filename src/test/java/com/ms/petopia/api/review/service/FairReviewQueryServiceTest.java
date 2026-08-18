package com.ms.petopia.api.review.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.review.dto.FairReviewListResponse;
import com.ms.petopia.api.review.dto.FairReviewListRow;
import com.ms.petopia.api.review.dto.FairReviewSummaryResponse;
import com.ms.petopia.api.review.dto.FairReviewSummaryRow;
import com.ms.petopia.api.review.dto.MyFairReviewListResponse;
import com.ms.petopia.api.review.dto.MyFairReviewRow;
import com.ms.petopia.api.review.mapper.FairReviewMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FairReviewQueryServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long USER_ID = 1L;

    @Mock
    private FairReviewMapper fairReviewMapper;
    @Mock
    private FairMapper fairMapper;

    @InjectMocks
    private FairReviewQueryService fairReviewQueryService;

    // ===== getReviewsByFair =====

    @Test
    @DisplayName("행사가 존재하면 리뷰 목록을 페이지네이션 형태로 반환한다")
    void getReviewsByFair_정상적으로_목록을_반환한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(existingFair());
        given(fairReviewMapper.countByFairId(FAIR_ID)).willReturn(5L);
        given(fairReviewMapper.selectListByFairId(FAIR_ID, 0L, 2))
                .willReturn(List.of(listRow(100L), listRow(101L)));

        FairReviewListResponse response = fairReviewQueryService.getReviewsByFair(FAIR_ID, 0, 2);

        assertThat(response.items()).hasSize(2);
        assertThat(response.totalElements()).isEqualTo(5L);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 행사면 FAIR_NOT_FOUND를 던진다")
    void getReviewsByFair_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(
                () -> fairReviewQueryService.getReviewsByFair(FAIR_ID, 0, 10),
                ErrorCode.FAIR_NOT_FOUND
        );
    }

    @Test
    @DisplayName("size가 상한(50)을 초과하면 INVALID_INPUT_VALUE를 던진다")
    void getReviewsByFair_size가_상한초과면_예외를_던진다() {
        assertErrorCode(
                () -> fairReviewQueryService.getReviewsByFair(FAIR_ID, 0, 51),
                ErrorCode.INVALID_INPUT_VALUE
        );
    }

    @Test
    @DisplayName("page가 음수면 INVALID_INPUT_VALUE를 던진다")
    void getReviewsByFair_page가_음수면_예외를_던진다() {
        assertErrorCode(
                () -> fairReviewQueryService.getReviewsByFair(FAIR_ID, -1, 10),
                ErrorCode.INVALID_INPUT_VALUE
        );
    }

    // ===== getSummaryByFair =====

    @Test
    @DisplayName("행사가 존재하면 평점 요약을 반환한다")
    void getSummaryByFair_정상적으로_요약을_반환한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(existingFair());
        FairReviewSummaryRow row = new FairReviewSummaryRow();
        row.setAverageRating(4.5);
        row.setReviewCount(2L);
        given(fairReviewMapper.selectSummaryByFairId(FAIR_ID)).willReturn(row);

        FairReviewSummaryResponse response = fairReviewQueryService.getSummaryByFair(FAIR_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviewCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("존재하지 않는 행사면 FAIR_NOT_FOUND를 던진다")
    void getSummaryByFair_존재하지않으면_예외를_던진다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        assertErrorCode(
                () -> fairReviewQueryService.getSummaryByFair(FAIR_ID),
                ErrorCode.FAIR_NOT_FOUND
        );
    }

    // ===== getMyReviews =====

    @Test
    @DisplayName("본인이 쓴 리뷰 목록을 페이지네이션 형태로 반환한다")
    void getMyReviews_정상적으로_목록을_반환한다() {
        given(fairReviewMapper.countByUserId(USER_ID)).willReturn(1L);
        given(fairReviewMapper.selectMyReviews(USER_ID, 0L, 10)).willReturn(List.of(myReviewRow()));

        MyFairReviewListResponse response = fairReviewQueryService.getMyReviews(USER_ID, 0, 10);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).fairName()).isEqualTo("펫박람회");
        assertThat(response.hasNext()).isFalse();
    }

    @Test
    @DisplayName("size가 상한(50)을 초과하면 INVALID_INPUT_VALUE를 던진다")
    void getMyReviews_size가_상한초과면_예외를_던진다() {
        assertErrorCode(
                () -> fairReviewQueryService.getMyReviews(USER_ID, 0, 100),
                ErrorCode.INVALID_INPUT_VALUE
        );
    }

    // ===== fixtures =====

    private Fair existingFair() {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        return fair;
    }

    private FairReviewListRow listRow(Long reviewId) {
        FairReviewListRow row = new FairReviewListRow();
        row.setReviewId(reviewId);
        row.setFairId(FAIR_ID);
        row.setUserId(USER_ID);
        row.setNickname("닉네임");
        row.setRating(5);
        row.setContent("좋아요");
        row.setVersion(0L);
        return row;
    }

    private MyFairReviewRow myReviewRow() {
        MyFairReviewRow row = new MyFairReviewRow();
        row.setReviewId(100L);
        row.setFairId(FAIR_ID);
        row.setFairName("펫박람회");
        row.setRating(5);
        row.setContent("좋아요");
        row.setVersion(0L);
        return row;
    }

    private void assertErrorCode(Runnable action, ErrorCode errorCode) {
        assertThatThrownBy(action::run)
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(errorCode);
    }
}
