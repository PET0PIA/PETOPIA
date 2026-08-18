package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 마이페이지 "내 리뷰" 목록 전용 조회 행. {@link FairReview}(순수 테이블 매핑)와 달리
 * 행사 이름·포스터를 fairs 테이블과 JOIN해서 함께 담는다 - {@link FairReviewListRow}가
 * users 테이블과 JOIN하는 것과 같은 패턴이다.
 */
@Getter
@Setter
public class MyFairReviewRow {

    private Long reviewId;
    private Long fairId;
    private String fairName;
    private String fairPosterImageUrl;
    private Integer rating;
    private String content;
    private boolean verifiedVisit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;
}
