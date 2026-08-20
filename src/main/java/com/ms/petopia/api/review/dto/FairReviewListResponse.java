package com.ms.petopia.api.review.dto;

import java.util.List;

/** 공개 리뷰 목록 응답(페이지네이션). NotificationListResponse와 같은 포맷을 따른다. */
public record FairReviewListResponse(
        List<FairReviewListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
