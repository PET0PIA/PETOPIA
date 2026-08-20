package com.ms.petopia.api.review.dto;

/**
 * 태그 수정 전 경고용 조회 응답. "이 태그가 이미 몇 건의 리뷰/부스 평가에 쓰였는지"를
 * 보여줘서, 관리자가 라벨을 바꾸거나 비활성화하기 전에 영향 범위를 알 수 있게 한다
 * (petopia-review-feature-plan 스킬의 "태그 마스터 잠그지 않되 경고 UX" 결정).
 */
public record FeedbackTagUsageResponse(
        Long tagId,
        String label,
        boolean active,
        long usageCount
) {
}
