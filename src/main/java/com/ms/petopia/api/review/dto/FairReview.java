package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * fair_reviews 테이블 매핑 객체. 스키마·설계 결정은
 * {@code V17__fair_reviews.sql}의 헤더 주석을 따른다.
 *
 * <p>record가 아니라 세터가 있는 클래스인 이유는 {@link com.ms.petopia.api.fair.dto.Fair}와
 * 동일하다 - MyBatis 기본 매핑이 세터 기반이라서다. API 요청/응답 DTO에는 이 객체를 그대로
 * 노출하지 않는다.
 */
@Getter
@Setter
@ToString
public class FairReview {

    private Long reviewId;

    /** 리뷰 대상 fairs.fair_id */
    private Long fairId;

    /** 작성자 users.user_id */
    private Long userId;

    /** 평점(1~5). 범위 검증은 서비스 계층에서 한다 */
    private Integer rating;

    private String content;

    /** 작성 시점에 이 사용자의 예매·방문 이력이 있었는지(뱃지 표시용 스냅샷). 이후 그 예약이
     * 취소되더라도 이 값은 소급 변경하지 않는다. */
    private boolean verifiedVisit;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
