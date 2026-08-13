package com.ms.petopia.api.review.controller;

import com.ms.petopia.api.review.dto.CreateFairReviewRequest;
import com.ms.petopia.api.review.dto.FairReviewListResponse;
import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.dto.FairReviewSummaryResponse;
import com.ms.petopia.api.review.dto.UpdateFairReviewRequest;
import com.ms.petopia.api.review.service.FairReviewQueryService;
import com.ms.petopia.api.review.service.FairReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 페어 리뷰 API.
 *
 * <p>작성(POST)은 SecurityConfig에서 로그인 여부만 검증한다 - 작성 자격을 예매·방문
 * 여부로 막지 않는다는 결정이라 role 제한도 없다(petopia-review-feature-plan 스킬 참고).
 *
 * <p>목록·요약 조회(GET)는 permitAll이다 - 방문 전에 누구나 리뷰를 미리 볼 수 있게 한다는
 * 결정.
 *
 * <p>수정(PATCH)·삭제(DELETE)는 로그인만 요구한다 - 본인이 작성한 리뷰인지는 서비스 계층
 * ({@link FairReviewService#update}/{@link FairReviewService#delete})에서 검증한다.
 */
@RestController
@RequestMapping("/api/fairs/{fairId}/reviews")
@RequiredArgsConstructor
public class FairReviewController {

    private final FairReviewService fairReviewService;
    private final FairReviewQueryService fairReviewQueryService;

    @PostMapping
    public ResponseEntity<FairReviewResponse> create(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long userId,
            @RequestBody CreateFairReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fairReviewService.create(fairId, userId, request));
    }

    @GetMapping
    public FairReviewListResponse list(
            @PathVariable Long fairId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return fairReviewQueryService.getReviewsByFair(fairId, page, size);
    }

    @GetMapping("/summary")
    public FairReviewSummaryResponse summary(@PathVariable Long fairId) {
        return fairReviewQueryService.getSummaryByFair(fairId);
    }

    @PatchMapping("/{reviewId}")
    public FairReviewResponse update(
            @PathVariable Long fairId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal Long userId,
            @RequestBody UpdateFairReviewRequest request
    ) {
        return fairReviewService.update(fairId, reviewId, userId, request);
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long fairId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal Long userId
    ) {
        fairReviewService.delete(fairId, reviewId, userId);
        return ResponseEntity.noContent().build();
    }
}
