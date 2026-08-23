package com.ms.petopia.api.review.controller;

import com.ms.petopia.api.review.dto.MyReviewListResponse;
import com.ms.petopia.api.review.service.FairReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 "내 리뷰" 조회 API. FairReviewController는 /api/fairs/{fairId}/reviews
 * 하위라 특정 행사 안에서만 쓸 수 있는데, 여기서는 로그인한 사용자가 여러 행사에 걸쳐 쓴
 * 리뷰를 한 번에 봐야 해서 경로를 분리했다(petopia-review-feature-plan 스킬 참고).
 *
 * <p>조회 전용이다 - 수정·본인 삭제 API는 없다(FairReviewService 클래스 주석 참고).
 */
@RestController
@RequestMapping("/api/reviews/me")
@RequiredArgsConstructor
public class MyReviewController {

    private final FairReviewService fairReviewService;

    @GetMapping
    public MyReviewListResponse list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return fairReviewService.listMine(userId, page, size);
    }
}
