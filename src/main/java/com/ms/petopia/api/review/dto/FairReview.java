package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * fair_reviews 테이블 매핑 객체(V39 재설계). 별점+자유서술 텍스트를 완전히 대체한 통합
 * 리뷰의 "행사 전체" 응답 부분만 담는다 - 태그 선택은 fair_review_tag_selections, 부스별
 * 평가는 booth_feedbacks로 분리돼 있다.
 *
 * <p>행사당 사용자 1건으로 제한한다(UNIQUE(fair_id,user_id), V39) - 재작성이 아니라
 * "새로 작성"만 지원하고 수정 API는 없다. 관리자 하드 삭제({@code FairReviewService#adminDeleteReview})는
 * 지원한다(petopia-review-feature-plan 스킬 참고).
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

    private CompanionType companionType;

    private VisitPurpose visitPurpose;

    /** 재방문 의향. 조직자 통계의 "재방문의향 %" 지표에 그대로 쓰인다. */
    private boolean wouldRevisit;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum CompanionType {
        ALONE, WITH_PET, WITH_FAMILY, WITH_FRIEND
    }

    public enum VisitPurpose {
        SHOPPING, EXPERIENCE, INFO, ETC
    }
}
