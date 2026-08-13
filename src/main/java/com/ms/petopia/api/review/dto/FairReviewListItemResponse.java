package com.ms.petopia.api.review.dto;

import java.time.LocalDateTime;

public record FairReviewListItemResponse(
        Long reviewId,
        Long fairId,
        Long userId,
        String nickname,
        Integer rating,
        String content,
        boolean verifiedVisit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static FairReviewListItemResponse from(FairReviewListRow row) {
        return new FairReviewListItemResponse(
                row.getReviewId(),
                row.getFairId(),
                row.getUserId(),
                row.getNickname(),
                row.getRating(),
                row.getContent(),
                row.isVerifiedVisit(),
                row.getCreatedAt(),
                row.getUpdatedAt()
        );
    }
}
