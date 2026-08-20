package com.ms.petopia.api.review.dto;

import java.util.List;

/**
 * 카테고리 하나의 긍정/개선 필요 TOP5. count 내림차순으로 최대 5건씩만 담는다(정렬·자르기는
 * FairReviewStatsService에서 처리).
 */
public record CategoryTagRanking(
        String category,
        List<TagCountItem> positiveTop,
        List<TagCountItem> negativeTop
) {
}
