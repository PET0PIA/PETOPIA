package com.ms.petopia.api.review.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 공개 리뷰 목록 항목 1건. 별점+자유서술 대신 선택한 scope=FAIR 태그 라벨 목록으로
 * 리뷰 내용을 보여준다.
 */
public record FairReviewListItemResponse(
        Long reviewId,
        String nickname,
        FairReview.CompanionType companionType,
        FairReview.VisitPurpose visitPurpose,
        boolean wouldRevisit,
        List<String> fairTagLabels,
        LocalDateTime createdAt
) {
}
