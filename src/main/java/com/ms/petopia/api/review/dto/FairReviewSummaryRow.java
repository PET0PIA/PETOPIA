package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * fair_id별 리뷰 평점 요약(평균 평점 + 개수) 조회 전용 행. 리뷰가 하나도 없으면
 * SQL AVG()가 NULL을 반환하므로 averageRating은 null일 수 있다.
 */
@Getter
@Setter
public class FairReviewSummaryRow {

    private Double averageRating;
    private long reviewCount;
}
