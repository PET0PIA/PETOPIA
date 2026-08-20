package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 공개 리뷰 목록 조회의 원본 row(users 조인 포함). 태그 라벨은 별도 배치 쿼리
 * (selectFairTagLabelsByReviewIds)로 가져와 서비스 계층에서 review_id 기준으로 묶는다 -
 * FairReviewStatsService가 TagCountRow를 카테고리별로 묶는 것과 같은 방식.
 */
@Getter
@Setter
public class FairReviewListRow {
    private Long reviewId;
    private String nickname;
    private FairReview.CompanionType companionType;
    private FairReview.VisitPurpose visitPurpose;
    private boolean wouldRevisit;
    private LocalDateTime createdAt;
}
