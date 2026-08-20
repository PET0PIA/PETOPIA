package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 태그별 선택 건수 집계 row. "만족도 상세 분석" 카테고리별 긍정/개선 TOP5를 만드는 재료 -
 * 실제 정렬·TOP5 자르기·카테고리 그룹핑은 서비스 계층(FairReviewStatsService)에서 한다.
 */
@Getter
@Setter
public class TagCountRow {
    private Long tagId;
    private String label;
    private String category;
    private FeedbackTag.Sentiment sentiment;
    private long count;
}
