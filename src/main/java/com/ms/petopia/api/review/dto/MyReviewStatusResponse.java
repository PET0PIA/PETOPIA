package com.ms.petopia.api.review.dto;

/**
 * "이 행사에 이미 리뷰를 작성했는지" + "이 행사를 실제로 방문했는지" 확인 응답.
 * FairDetailPage의 리뷰 버튼을 "리뷰 작성"(hasVisited=true, alreadyReviewed=false) /
 * "리뷰 작성 완료"(alreadyReviewed=true) / "리뷰 작성 불가"(hasVisited=false) 세 상태로
 * 분기하는 데 쓴다(Phase 5). hasVisited는 alreadyReviewed와 무관하게 항상 계산한다 -
 * 이미 작성했다면 제출 시점에 방문 검증을 통과했다는 뜻이라 사실상 항상 true지만, 별도
 * 필드로 내려줘서 프론트가 각 상태를 독립적으로 판단할 수 있게 한다.
 */
public record MyReviewStatusResponse(
        boolean alreadyReviewed,
        Long reviewId,
        boolean hasVisited
) {
}
