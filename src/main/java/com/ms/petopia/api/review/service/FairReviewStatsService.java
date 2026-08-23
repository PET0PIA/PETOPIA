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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

/**
 * 행사 관리자 통계 페이지 중 리뷰(태그) 데이터 기반 부분(Phase 6). 예약·입장 데이터 기반
 * 부분(총방문자/실입장자/총예약자/시간대별·일자별 추이/방문 부스 수 분포 등)은 이미
 * meltingujin이 만든 {@code com.ms.petopia.api.statistics} 도메인(/api/fairs/{fairId}/
 * visit-stats, /hourly-entry-trend, /reservation-dashboard, /booth-visit-pattern 등)이
 * 제공하므로 여기서 다시 만들지 않는다 - 프론트(Phase 6)가 두 API를 함께 호출해서 한 화면에
 * 합쳐 보여준다(petopia-review-feature-plan 스킬 참고).
 */
@Service
@RequiredArgsConstructor
public class FairReviewStatsService {

    /** V39 시드 데이터의 scope=FAIR 카테고리 순서 그대로 - 선택 건수가 0건인 카테고리도
     * 응답에 빠지지 않고 빈 배열로 나오게 고정 순서를 쓴다. */
    private static final List<String> FAIR_CATEGORIES = List.of(
            "GUIDE_OPERATION", "SAFETY_HYGIENE", "WAIT_FLOW", "PET_CONVENIENCE",
            "FACILITY", "PRICE_VALUE", "CONTENT_PROGRAM"
    );
    private static final int TOP_N = 5;

    private final FairReviewMapper fairReviewMapper;
    private final FairAdminAccessGuard fairAdminAccessGuard;

    @Transactional(readOnly = true)
    public FairReviewStatsResponse getStats(Long fairId) {
        fairAdminAccessGuard.checkAssigned(fairId);

        long reviewCount = fairReviewMapper.countByFairId(fairId);
        List<CountItem> companionTypeDistribution = toCountItems(
                fairReviewMapper.selectCompanionTypeDistribution(fairId), reviewCount);
        List<CountItem> visitPurposeDistribution = toCountItems(
                fairReviewMapper.selectVisitPurposeDistribution(fairId), reviewCount);
        double revisitRate = reviewCount == 0 ? 0.0
                : (double) fairReviewMapper.countRevisitByFairId(fairId) / reviewCount;

        List<TagCountRow> tagCounts = fairReviewMapper.selectFairTagCounts(fairId);
        List<CategoryTagRanking> rankings = FAIR_CATEGORIES.stream()
                .map(category -> buildCategoryRanking(category, tagCounts, reviewCount))
                .toList();

        return new FairReviewStatsResponse(reviewCount, companionTypeDistribution, visitPurposeDistribution,
                revisitRate, rankings);
    }

    private CategoryTagRanking buildCategoryRanking(String category, List<TagCountRow> allTagCounts, long reviewCount) {
        List<TagCountItem> positive = allTagCounts.stream()
                .filter(row -> category.equals(row.getCategory()) && row.getSentiment() == FeedbackTag.Sentiment.POSITIVE)
                .sorted(Comparator.comparingLong(TagCountRow::getCount).reversed())
                .limit(TOP_N)
                .map(row -> toTagCountItem(row, reviewCount))
                .toList();
        List<TagCountItem> negative = allTagCounts.stream()
                .filter(row -> category.equals(row.getCategory()) && row.getSentiment() == FeedbackTag.Sentiment.NEGATIVE)
                .sorted(Comparator.comparingLong(TagCountRow::getCount).reversed())
                .limit(TOP_N)
                .map(row -> toTagCountItem(row, reviewCount))
                .toList();
        return new CategoryTagRanking(category, positive, negative);
    }

    private TagCountItem toTagCountItem(TagCountRow row, long reviewCount) {
        double ratio = reviewCount == 0 ? 0.0 : (double) row.getCount() / reviewCount;
        return new TagCountItem(row.getTagId(), row.getLabel(), row.getCount(), ratio);
    }

    private List<CountItem> toCountItems(List<LabeledCountRow> rows, long total) {
        return rows.stream()
                .map(row -> new CountItem(row.getLabel(), row.getCount(),
                        total == 0 ? 0.0 : (double) row.getCount() / total))
                .toList();
    }
}
