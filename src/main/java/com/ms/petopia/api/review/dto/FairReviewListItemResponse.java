package com.ms.petopia.api.review.dto;

import java.time.LocalDateTime;

public record FairReviewListItemResponse(
        Long reviewId,
        Long fairId,
        String nickname,
        Integer rating,
        String content,
        boolean verifiedVisit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
    public static FairReviewListItemResponse from(FairReviewListRow row) {
        return new FairReviewListItemResponse(
                row.getReviewId(),
                row.getFairId(),
                row.getNickname(),
                row.getRating(),
                row.getContent(),
                row.isVerifiedVisit(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getVersion()
        );
    }
}
