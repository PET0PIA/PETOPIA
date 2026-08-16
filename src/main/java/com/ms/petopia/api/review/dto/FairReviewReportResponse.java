package com.ms.petopia.api.review.dto;

import java.time.LocalDateTime;

public record FairReviewReportResponse(
        Long reviewReportId,
        Long reviewId,
        String reason,
        String reasonDetail,
        LocalDateTime createdAt
) {
    public static FairReviewReportResponse from(FairReviewReport report) {
        return new FairReviewReportResponse(
                report.getReviewReportId(),
                report.getReviewId(),
                report.getReason(),
                report.getReasonDetail(),
                report.getCreatedAt()
        );
    }
}
