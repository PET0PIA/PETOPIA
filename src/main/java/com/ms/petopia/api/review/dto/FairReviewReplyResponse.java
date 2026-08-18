package com.ms.petopia.api.review.dto;

import java.time.LocalDateTime;

public record FairReviewReplyResponse(
        Long reviewReplyId,
        Long reviewId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String content
) {
    public static FairReviewReplyResponse from(FairReviewReply reply) {
        return new FairReviewReplyResponse(
                reply.getReviewReplyId(),
                reply.getReviewId(),
                reply.getCreatedAt(),
                reply.getUpdatedAt(),
                reply.getContent()
        );
    }
}
