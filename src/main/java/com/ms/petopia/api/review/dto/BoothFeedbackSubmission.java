package com.ms.petopia.api.review.dto;

import java.util.List;

/**
 * 리뷰 제출 요청 안의 부스별 평가 1건.
 *
 * @param boothId           평가 대상 booth.booth_id
 * @param tagIds            선택한 scope=BOOTH 태그 id 목록
 * @param purchaseBehavior  구매행동. 건너뛸 수 있어 null 허용
 */
public record BoothFeedbackSubmission(
        Long boothId,
        List<Long> tagIds,
        BoothFeedback.PurchaseBehavior purchaseBehavior
) {
}
