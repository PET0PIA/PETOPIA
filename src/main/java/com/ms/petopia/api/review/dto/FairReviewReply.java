package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * fair_review_replies 테이블 매핑 객체. 스키마·설계 결정은
 * {@code V32__fair_review_replies.sql}의 헤더 주석을 따른다. 리뷰 하나당 1개만 존재한다
 * (review_id UNIQUE).
 */
@Getter
@Setter
@ToString
public class FairReviewReply {

    private Long reviewReplyId;

    /** 답글 대상 fair_reviews.review_id */
    private Long reviewId;

    /** fairs.fair_id. 조회 편의용 비정규화 */
    private Long fairId;

    /** 작성한 행사 담당자 users.user_id */
    private Long adminUserId;

    private String content;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
