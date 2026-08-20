package com.ms.petopia.api.review.dto;

import java.time.LocalDateTime;

public record FairReviewResponse(
        Long reviewId,
        Long fairId,
        FairReview.CompanionType companionType,
        FairReview.VisitPurpose visitPurpose,
        boolean wouldRevisit,
        LocalDateTime createdAt
) {
    public static FairReviewResponse from(FairReview review) {
        return new FairReviewResponse(
                review.getReviewId(),
                review.getFairId(),
                review.getCompanionType(),
                review.getVisitPurpose(),
                review.isWouldRevisit(),
                review.getCreatedAt()
        );
    }
}
