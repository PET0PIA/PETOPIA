package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * booth_feedbacks 테이블 매핑 객체(V39). 리뷰 1건에 딸린 부스별 평가 - 리뷰 마법사에서
 * 최대 3개 부스까지 선택해 반복 작성한다.
 *
 * <p>fair_id/business_id는 booth_visits와 같은 이유로 통계 조회 편의를 위해 비정규화해서
 * 같이 저장한다. 한 사용자가 같은 부스에 평가를 여러 번 남기지 못하도록
 * UNIQUE(booth_id,user_id) 제약이 있다.
 */
@Getter
@Setter
@ToString
public class BoothFeedback {

    private Long boothFeedbackId;

    /** 소속 리뷰 fair_reviews.review_id */
    private Long reviewId;

    /** 평가 대상 booth.booth_id */
    private Long boothId;

    /** fairs.fair_id. 통계용 비정규화 */
    private Long fairId;

    /** business.business_id. 통계용 비정규화 */
    private Long businessId;

    /** users.user_id. 통계용 비정규화(review_id로도 알 수 있음) */
    private Long userId;

    /** 응답을 건너뛸 수 있어 null 허용 */
    private PurchaseBehavior purchaseBehavior;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum PurchaseBehavior {
        PURCHASED, FOLLOWED_SNS, LOOKED_ONLY
    }
}
