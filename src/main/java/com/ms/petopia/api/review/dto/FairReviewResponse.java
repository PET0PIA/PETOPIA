package com.ms.petopia.api.review.dto;

import java.time.LocalDateTime;

/**
 * 리뷰 작성/조회 응답.
 *
 * @param verifiedVisit 작성 시점에 이 사용자의 예매·방문 이력이 있었는지(뱃지 표시용 스냅샷).
 *                       지금은 항상 false다 - Reservation 도메인에 이 조회용 내부 계약 API가
 *                       아직 없어서, 그게 준비되면 실제 값으로 채우기로 했다
 *                       (petopia-review-feature-plan 스킬 참고).
 */
public record FairReviewResponse(
        Long reviewId,
        Long fairId,
        Long userId,
        Integer rating,
        String content,
        boolean verifiedVisit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
    public static FairReviewResponse from(FairReview review) {
        return new FairReviewResponse(
                review.getReviewId(),
                review.getFairId(),
                review.getUserId(),
                review.getRating(),
                review.getContent(),
                review.isVerifiedVisit(),
                review.getCreatedAt(),
                review.getUpdatedAt(),
                review.getVersion()
        );
    }
}
