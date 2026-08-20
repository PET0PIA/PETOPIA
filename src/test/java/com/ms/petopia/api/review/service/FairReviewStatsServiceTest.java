package com.ms.petopia.api.review.service;

import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.review.dto.CategoryTagRanking;
import com.ms.petopia.api.review.dto.CountItem;
import com.ms.petopia.api.review.dto.FairReviewStatsResponse;
import com.ms.petopia.api.review.dto.FeedbackTag;
import com.ms.petopia.api.review.dto.LabeledCountRow;
import com.ms.petopia.api.review.dto.TagCountItem;
import com.ms.petopia.api.review.dto.TagCountRow;
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
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/*
 * FairReviewStatsService 단위 테스트. Mapper는 Mock으로 대체하고, 분포·재방문율 계산과
 * 카테고리별 태그 TOP5 집계 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class FairReviewStatsServiceTest {

    private static final Long FAIR_ID = 10L;

    @Mock
    private FairReviewMapper fairReviewMapper;

    @Mock
    private FairAdminAccessGuard fairAdminAccessGuard;

    @InjectMocks
    private FairReviewStatsService fairReviewStatsService;

    @Test
    @DisplayName("담당 행사가 아니면(FairAdminAccessGuard 거부) 집계하지 않고 예외를 전파한다")
    void getStats_담당행사아니면_예외를_던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED)).given(fairAdminAccessGuard).checkAssigned(FAIR_ID);

        assertThatThrownBy(() -> fairReviewStatsService.getStats(FAIR_ID))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(fairReviewMapper, never()).countByFairId(FAIR_ID);
    }

    @Test
    @DisplayName("리뷰가 하나도 없으면 재방문율은 0이고 카테고리는 7개 전부 빈 목록으로 내려온다")
    void getStats_리뷰없으면_0으로_반환한다() {
        given(fairReviewMapper.countByFairId(FAIR_ID)).willReturn(0L);
        given(fairReviewMapper.selectCompanionTypeDistribution(FAIR_ID)).willReturn(List.of());
        given(fairReviewMapper.selectVisitPurposeDistribution(FAIR_ID)).willReturn(List.of());
        given(fairReviewMapper.selectFairTagCounts(FAIR_ID)).willReturn(List.of());

        FairReviewStatsResponse response = fairReviewStatsService.getStats(FAIR_ID);

        assertThat(response.reviewCount()).isZero();
        assertThat(response.revisitRate()).isZero();
        assertThat(response.fairTagRankings()).hasSize(7);
        assertThat(response.fairTagRankings()).allSatisfy(ranking -> {
            assertThat(ranking.positiveTop()).isEmpty();
            assertThat(ranking.negativeTop()).isEmpty();
        });
        // reviewCount가 0이면 나누기 대상이 없으므로 재방문 집계 쿼리 자체를 호출하지 않는다(0으로 short-circuit).
        verify(fairReviewMapper, never()).countRevisitByFairId(FAIR_ID);
    }

    @Test
    @DisplayName("동반유형·방문목적 분포와 재방문율을 리뷰 수 대비 비율로 계산한다")
    void getStats_분포와_재방문율을_계산한다() {
        given(fairReviewMapper.countByFairId(FAIR_ID)).willReturn(10L);
        given(fairReviewMapper.countRevisitByFairId(FAIR_ID)).willReturn(6L);
        given(fairReviewMapper.selectCompanionTypeDistribution(FAIR_ID)).willReturn(List.of(countRow("WITH_PET", 7)));
        given(fairReviewMapper.selectVisitPurposeDistribution(FAIR_ID)).willReturn(List.of(countRow("SHOPPING", 5)));
        given(fairReviewMapper.selectFairTagCounts(FAIR_ID)).willReturn(List.of());

        FairReviewStatsResponse response = fairReviewStatsService.getStats(FAIR_ID);

        assertThat(response.revisitRate()).isEqualTo(0.6);
        CountItem companion = response.companionTypeDistribution().get(0);
        assertThat(companion.key()).isEqualTo("WITH_PET");
        assertThat(companion.count()).isEqualTo(7);
        assertThat(companion.ratio()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("카테고리별로 긍정·부정 태그를 각각 건수 내림차순 TOP5까지만 담는다")
    void getStats_카테고리별_TOP5를_정렬해서_반환한다() {
        given(fairReviewMapper.countByFairId(FAIR_ID)).willReturn(100L);
        given(fairReviewMapper.countRevisitByFairId(FAIR_ID)).willReturn(0L);
        given(fairReviewMapper.selectCompanionTypeDistribution(FAIR_ID)).willReturn(List.of());
        given(fairReviewMapper.selectVisitPurposeDistribution(FAIR_ID)).willReturn(List.of());

        // GUIDE_OPERATION 카테고리에 긍정 태그 6개(TOP5로 잘려야 함), 부정 태그 1개
        List<TagCountRow> rows = new java.util.ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            rows.add(tagCountRow((long) i, "긍정" + i, "GUIDE_OPERATION", FeedbackTag.Sentiment.POSITIVE, i * 10L));
        }
        rows.add(tagCountRow(99L, "부정1", "GUIDE_OPERATION", FeedbackTag.Sentiment.NEGATIVE, 3L));
        given(fairReviewMapper.selectFairTagCounts(FAIR_ID)).willReturn(rows);

        FairReviewStatsResponse response = fairReviewStatsService.getStats(FAIR_ID);

        CategoryTagRanking guideOperation = response.fairTagRankings().stream()
                .filter(r -> r.category().equals("GUIDE_OPERATION"))
                .findFirst().orElseThrow();

        assertThat(guideOperation.positiveTop()).hasSize(5);
        // count가 가장 큰 "긍정6"(60건)이 1등이어야 한다
        assertThat(guideOperation.positiveTop().get(0).label()).isEqualTo("긍정6");
        assertThat(guideOperation.positiveTop().get(0).count()).isEqualTo(60L);
        assertThat(guideOperation.negativeTop()).hasSize(1);
        assertThat(guideOperation.negativeTop().get(0).label()).isEqualTo("부정1");

        TagCountItem top = guideOperation.positiveTop().get(0);
        assertThat(top.ratio()).isEqualTo(0.6); // 60/100
    }

    private LabeledCountRow countRow(String label, long count) {
        LabeledCountRow row = new LabeledCountRow();
        row.setLabel(label);
        row.setCount(count);
        return row;
    }

    private TagCountRow tagCountRow(Long tagId, String label, String category, FeedbackTag.Sentiment sentiment, long count) {
        TagCountRow row = new TagCountRow();
        row.setTagId(tagId);
        row.setLabel(label);
        row.setCategory(category);
        row.setSentiment(sentiment);
        row.setCount(count);
        return row;
    }
}
