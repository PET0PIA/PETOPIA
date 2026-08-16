package com.ms.petopia.api.review.dto;

/** 답글 작성/수정 요청. 작성·수정이 필드가 같아 하나로 공유한다. */
public record FairReviewReplyRequest(
        String content
) {
}
