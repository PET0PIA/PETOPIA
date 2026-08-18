package com.ms.petopia.api.review.dto;

import java.util.List;

/** FairReviewListResponse와 같은 페이지네이션 응답 형태(items/page/size/totalElements/totalPages/hasNext). */
public record MyFairReviewListResponse(
        List<MyFairReviewResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
