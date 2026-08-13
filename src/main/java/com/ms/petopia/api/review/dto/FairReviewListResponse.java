package com.ms.petopia.api.review.dto;

import java.util.List;

/** NotificationListResponse와 같은 페이지네이션 응답 형태(items/page/size/totalElements/totalPages/hasNext). */
public record FairReviewListResponse(
        List<FairReviewListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
