package com.ms.petopia.api.review.controller;

import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.dto.MyReviewStatusResponse;
import com.ms.petopia.api.review.dto.SubmitFairReviewRequest;
import com.ms.petopia.api.review.service.FairReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    /** FairDetailPage의 "리뷰 남기기"/"내 리뷰 보기" 버튼 라벨 분기용(Phase 5). */
    @GetMapping("/status")
    public MyReviewStatusResponse status(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long userId
    ) {
        return fairReviewService.checkStatus(fairId, userId);
    }
}
