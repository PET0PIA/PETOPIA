package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * fair_review_reports 테이블 매핑 객체. 스키마·설계 결정은
 * {@code V31__fair_review_reports.sql}의 헤더 주석을 따른다.
 */
@Getter
@Setter
@ToString
public class FairReviewReport {

    private Long reviewReportId;

    /** 신고 대상 fair_reviews.review_id */
    private Long reviewId;

    /** 신고자 users.user_id */
    private Long reporterUserId;

    /** SPAM / ABUSE / FALSE_INFO / ETC. 값 검증은 서비스 계층에서 한다 */
    private String reason;

    /** reason=ETC일 때만 채워진다 */
    private String reasonDetail;

    private LocalDateTime createdAt;
}
