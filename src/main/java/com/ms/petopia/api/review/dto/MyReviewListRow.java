package com.ms.petopia.api.review.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * "내 리뷰" 목록 조회의 원본 row(fairs 조인 포함). 공개 목록(FairReviewListRow)은 users를
 * 조인해 작성자 닉네임을 보여주지만, 이건 여러 행사에 걸친 내 리뷰를 보여줘야 해서 fairs를
 * 조인해 행사명·포스터 이미지를 담는다. 태그 라벨은 selectFairTagLabelsByReviewIds로 별도
 * 배치 조회해 서비스 계층에서 review_id 기준으로 묶는다(FairReviewService#listPublic과 동일).
 */
@Getter
@Setter
public class MyReviewListRow {
    private Long reviewId;
    private Long fairId;
    private String fairName;
    private String posterImageUrl;
    private FairReview.CompanionType companionType;
    private FairReview.VisitPurpose visitPurpose;
    private boolean wouldRevisit;
    private LocalDateTime createdAt;
}
