package com.ms.petopia.api.review.controller;

import com.ms.petopia.api.review.dto.FairReviewListResponse;
import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.dto.FairReviewSummaryResponse;
import com.ms.petopia.api.review.dto.MyReviewStatusResponse;
import com.ms.petopia.api.review.dto.SubmitFairReviewRequest;
import com.ms.petopia.api.review.service.FairReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 리뷰(태그 기반) 제출 API. 별점+자유서술 리뷰(FairReviewController, V17~V35)를 완전히
 * 대체한다(petopia-review-feature-plan 스킬 참고).
 */
@RestController
@RequestMapping("/api/fairs/{fairId}/reviews")
@RequiredArgsConstructor
public class FairReviewController {

    private final FairReviewService fairReviewService;

    @PostMapping
    public ResponseEntity<FairReviewResponse> submit(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long userId,
            @RequestBody SubmitFairReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fairReviewService.submit(fairId, userId, request));
    }

    /** FairDetailPage의 리뷰 버튼 상태(작성/작성완료/작성불가) 분기용(Phase 5). */
    @GetMapping("/status")
    public MyReviewStatusResponse status(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long userId
    ) {
        return fairReviewService.checkStatus(fairId, userId);
    }

    /** 공개 리뷰 목록(최신순 페이지네이션, 인증 불필요). page는 0부터. */
    @GetMapping
    public FairReviewListResponse list(
            @PathVariable Long fairId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return fairReviewService.listPublic(fairId, page, size);
    }

    /** 공개 리뷰 요약(리뷰 수·재방문 의향 비율, 인증 불필요). */
    @GetMapping("/summary")
    public FairReviewSummaryResponse summary(@PathVariable Long fairId) {
        return fairReviewService.getPublicSummary(fairId);
    }

    /** 행사담당자/최고관리자의 부적절한 리뷰 삭제(하드 삭제, 딸린 부스 평가까지 함께 삭제).
     * 권한 검증은 서비스 계층의 FairAdminAccessGuard가 담당한다. */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(@PathVariable Long fairId, @PathVariable Long reviewId) {
        fairReviewService.adminDeleteReview(fairId, reviewId);
        return ResponseEntity.noContent().build();
    }
}
