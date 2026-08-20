package com.ms.petopia.api.review.dto;

/**
 * "이 행사에 이미 리뷰를 작성했는지" 확인 응답. FairDetailPage의 "리뷰 남기기"/"내 리뷰
 * 보기" 버튼 라벨 분기에 쓰인다(Phase 5).
 */
public record MyReviewStatusResponse(
        boolean alreadyReviewed,
        Long reviewId
) {
}
