package com.ms.petopia.api.review.dto;

/**
 * 공개 리뷰 요약. 별점 평균 대신 재방문 의향 비율을 대표 지표로 보여준다(V39 재설계 -
 * 별점 자체가 없어졌다).
 */
public record FairReviewSummaryResponse(
        Long fairId,
        long reviewCount,
        double revisitRate
) {
}
