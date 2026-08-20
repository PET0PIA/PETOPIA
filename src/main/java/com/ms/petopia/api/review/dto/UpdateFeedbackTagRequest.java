package com.ms.petopia.api.review.dto;

/**
 * 태그 마스터 수정 요청(SUPER_ADMIN 전용). label·sort_order·active만 바꿀 수 있다 -
 * scope·category·sentiment는 태그의 정체성이라 수정 대상이 아니다(바꾸고 싶으면 비활성화하고
 * 새로 만든다). active를 false로 내리는 것이 soft delete다 - 물리 삭제 API는 없다.
 */
public record UpdateFeedbackTagRequest(
        String label,
        Integer sortOrder,
        Boolean active
) {
}
