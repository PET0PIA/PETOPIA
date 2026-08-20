package com.ms.petopia.api.review.dto;

import java.util.List;

/**
 * 통합 리뷰 제출 요청. 마법사 전체(페르소나 → 행사 태그 → 부스 선택·평가 → 재방문의향)를
 * 한 번에 제출한다 - 중간에 새로 저장하는 단계가 없어 서버는 트랜잭션 하나로 처리한다.
 *
 * @param companionType  동반유형(필수)
 * @param visitPurpose   방문목적(필수)
 * @param wouldRevisit   재방문 의향(필수)
 * @param fairTagIds     선택한 scope=FAIR 태그 id 목록(다중선택, 0개 이상)
 * @param booths         부스별 평가. 최대 3건까지(petopia-review-feature-plan 스킬 참고)
 */
public record SubmitFairReviewRequest(
        FairReview.CompanionType companionType,
        FairReview.VisitPurpose visitPurpose,
        Boolean wouldRevisit,
        List<Long> fairTagIds,
        List<BoothFeedbackSubmission> booths
) {
}
