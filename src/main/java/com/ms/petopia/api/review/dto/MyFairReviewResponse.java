package com.ms.petopia.api.review.dto;

import java.time.LocalDateTime;

/** 마이페이지 "내 리뷰" 목록 한 건. userId는 요청자 본인이라 굳이 담지 않는다. */
public record MyFairReviewResponse(
        Long reviewId,
        Long fairId,
        String fairName,
        String fairPosterImageUrl,
        Integer rating,
        String content,
        boolean verifiedVisit,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Long version
) {
    public static MyFairReviewResponse from(MyFairReviewRow row) {
        return new MyFairReviewResponse(
                row.getReviewId(),
                row.getFairId(),
                row.getFairName(),
                row.getFairPosterImageUrl(),
                row.getRating(),
                row.getContent(),
                row.isVerifiedVisit(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                row.getVersion()
        );
    }
}
