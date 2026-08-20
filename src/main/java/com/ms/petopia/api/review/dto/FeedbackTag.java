package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * feedback_tags 테이블 매핑 객체(V39). 행사 전체용(FAIR)·부스 개별용(BOOTH) 태그를 하나의
 * 테이블에 scope로 구분해서 담는다.
 *
 * <p>태그 마스터는 잠그지 않는다 - 행사가 리뷰를 받기 시작한 뒤에도 추가·수정·soft delete가
 * 가능하다. 다만 물리 삭제는 절대 하지 않는다(active=false만 허용) - 이미 쌓인 선택 이력
 * (fair_review_tag_selections/booth_feedback_selections)이 항상 tag_id를 가리키고 라벨을
 * 중복 저장하지 않으므로, 라벨을 나중에 수정해도 과거 통계에 자동으로 반영된다
 * (petopia-review-feature-plan 스킬 참고).
 */
@Getter
@Setter
@ToString
public class FeedbackTag {

    private Long tagId;

    private Scope scope;

    /** scope별로 유효한 값이 다르다. FAIR: GUIDE_OPERATION/SAFETY_HYGIENE/WAIT_FLOW/
     * PET_CONVENIENCE/FACILITY/PRICE_VALUE/CONTENT_PROGRAM. BOOTH: CONSULTATION/PRODUCT/
     * EXPERIENCE/PRICE_BENEFIT/BOOTH_ENVIRONMENT. DB의 CK_FEEDBACK_TAGS_CATEGORY가 한 번 더
     * 막아준다. */
    private String category;

    private Sentiment sentiment;

    private String label;

    private Integer sortOrder;

    /** 0이면 신규 선택 불가(soft delete). is_active 컬럼과 JavaBean 프로퍼티명이 다르므로
     * XML에서 반드시 {@code is_active AS active}로 명시 별칭해야 한다(FairReview.verifiedVisit
     * 관례). */
    private boolean active;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public enum Scope {
        FAIR, BOOTH
    }

    public enum Sentiment {
        POSITIVE, NEGATIVE
    }
}
