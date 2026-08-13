package com.ms.petopia.api.review.controller;

import com.ms.petopia.api.review.dto.CreateFairReviewRequest;
import com.ms.petopia.api.review.dto.FairReviewResponse;
import com.ms.petopia.api.review.service.FairReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 페어 리뷰 API. SecurityConfig에서 로그인 여부만 검증한다 - 작성 자격을 예매·방문
 * 여부로 막지 않는다는 결정이라 role 제한도 없다(petopia-review-feature-plan 스킬 참고).
 */
@RestController
@RequestMapping("/api/fairs/{fairId}/reviews")
@RequiredArgsConstructor
public class FairReviewController {

    private final FairReviewService fairReviewService;

    @PostMapping
    public ResponseEntity<FairReviewResponse> create(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long userId,
            @RequestBody CreateFairReviewRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fairReviewService.create(fairId, userId, request));
    }
}
