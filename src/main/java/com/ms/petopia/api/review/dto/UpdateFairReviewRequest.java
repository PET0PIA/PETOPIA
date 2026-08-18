package com.ms.petopia.api.review.dto;

/**
 * 리뷰 수정 요청.
 *
 * @param rating  평점(1~5). 범위 검증은 서비스 계층에서 하고, V17의 CK_FAIR_REVIEWS_RATING이
 *                DB에서도 한 번 더 막아준다.
 * @param content 리뷰 내용.
 * @param version 클라이언트가 조회 시점에 받은 낙관적 락 버전(V35). 그 사이 다른 곳에서 먼저
 *                수정했으면 이 값이 최신 버전과 달라져 REVIEW_VERSION_CONFLICT(409)가 난다.
 */
public record UpdateFairReviewRequest(
        Integer rating,
        String content,
        Long version
) {
}
