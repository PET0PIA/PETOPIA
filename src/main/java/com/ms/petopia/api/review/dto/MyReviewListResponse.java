package com.ms.petopia.api.review.dto;

import java.util.List;

/** "내 리뷰" 목록 응답(페이지네이션). FairReviewListResponse와 같은 포맷을 따른다. */
public record MyReviewListResponse(
        List<MyReviewListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {
}
