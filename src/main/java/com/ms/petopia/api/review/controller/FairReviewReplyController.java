package com.ms.petopia.api.review.controller;

import com.ms.petopia.api.review.dto.FairReviewReplyRequest;
import com.ms.petopia.api.review.dto.FairReviewReplyResponse;
import com.ms.petopia.api.review.service.FairReviewReplyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 담당자 리뷰 답글 API. FairReviewController와 별도 컨트롤러로 뒀다 - 답글은 리뷰
 * 하나당 1개뿐이라 목록/페이징이 없고, 작성자도 다르다(EVENT_ADMIN/SUPER_ADMIN).
 *
 * <p>작성(POST)·수정(PATCH)은 SecurityConfig에서 role만 걸러주고, "이 행사 담당자인지"는
 * FairReviewReplyService가 FairAdminAccessGuard로 한 번 더 확인한다. 조회(GET)는 permitAll -
 * 답글도 리뷰처럼 누구나 볼 수 있어야 한다.
 */
@RestController
@RequestMapping("/api/fairs/{fairId}/reviews/{reviewId}/reply")
@RequiredArgsConstructor
public class FairReviewReplyController {

    private final FairReviewReplyService fairReviewReplyService;

    @PostMapping
    public ResponseEntity<FairReviewReplyResponse> create(
            @PathVariable Long fairId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody FairReviewReplyRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(fairReviewReplyService.create(fairId, reviewId, adminUserId, request));
    }

    @PatchMapping
    public FairReviewReplyResponse update(
            @PathVariable Long fairId,
            @PathVariable Long reviewId,
            @RequestBody FairReviewReplyRequest request
    ) {
        return fairReviewReplyService.update(fairId, reviewId, request);
    }

    @GetMapping
    public FairReviewReplyResponse get(
            @PathVariable Long fairId,
            @PathVariable Long reviewId
    ) {
        return fairReviewReplyService.get(fairId, reviewId);
    }
}
