package com.ms.petopia.api.review.dto;

/**
 * 태그 마스터 신규 등록 요청(SUPER_ADMIN 전용). category 값은 scope에 따라 유효한 값이
 * 다르다 - DB의 CK_FEEDBACK_TAGS_CATEGORY가 최종 검증을 한 번 더 한다.
 */
public record CreateFeedbackTagRequest(
        FeedbackTag.Scope scope,
        String category,
        FeedbackTag.Sentiment sentiment,
        String label,
        Integer sortOrder
) {
}
