package com.ms.petopia.api.review.dto;

/**
 * 리뷰 작성 요청.
 *
 * @param rating  평점(1~5). 범위 검증은 서비스 계층에서 하고, V17의 CK_FAIR_REVIEWS_RATING이
 *                DB에서도 한 번 더 막아준다.
 * @param content 리뷰 내용.
 */
public record CreateFairReviewRequest(
        Integer rating,
        String content
) {
}
