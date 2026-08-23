package com.ms.petopia.api.review.dto;

import java.util.List;

/**
 * 행사 관리자 통계 페이지 중 리뷰(태그) 데이터 기반 부분(Phase 6). 예약·입장 데이터 기반
 * 부분(총방문자/실입장자/총예약자/시간대별·일자별 추이/방문 부스 수 분포)은 이 응답에
 * 포함하지 않는다 - com.ms.petopia.api.fairstats 패키지가 별도로 제공한다
 * (petopia-review-feature-plan 스킬의 "리뷰 데이터도 넣는 것" 결정 참고).
 */
public record FairReviewStatsResponse(
        long reviewCount,
        List<CountItem> companionTypeDistribution,
        List<CountItem> visitPurposeDistribution,
        double revisitRate,
        List<CategoryTagRanking> fairTagRankings
) {
}
