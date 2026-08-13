package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 리뷰 목록 조회 전용 조회 행. {@link FairReview}(순수 테이블 매핑)와 달리 작성자 닉네임을
 * users 테이블과 JOIN해서 함께 담는다 - NotificationMapper#selectByUserId(NotificationListRow)와
 * 같은 패턴이다.
 */
@Getter
@Setter
public class FairReviewListRow {

    private Long reviewId;
    private Long fairId;
    private Long userId;
    private String nickname;
    private Integer rating;
    private String content;
    private boolean verifiedVisit;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
