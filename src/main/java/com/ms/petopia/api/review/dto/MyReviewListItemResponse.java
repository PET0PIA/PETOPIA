package com.ms.petopia.api.review.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * "내 리뷰" 목록 항목 1건. 공개 목록(FairReviewListItemResponse)과 달리 작성자 닉네임 대신
 * 어느 행사에 쓴 리뷰인지(fairId/fairName/posterImageUrl)를 담는다 - 마이페이지에서는 여러
 * 행사에 걸친 리뷰를 한 화면에 보여줘야 하기 때문이다.
 */
public record MyReviewListItemResponse(
        Long reviewId,
        Long fairId,
        String fairName,
        String posterImageUrl,
        FairReview.CompanionType companionType,
        FairReview.VisitPurpose visitPurpose,
        boolean wouldRevisit,
        List<String> fairTagLabels,
        LocalDateTime createdAt
) {
}
