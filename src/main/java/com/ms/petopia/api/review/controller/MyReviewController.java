package com.ms.petopia.api.review.controller;

import com.ms.petopia.api.review.dto.MyFairReviewListResponse;
import com.ms.petopia.api.review.service.FairReviewQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 마이페이지 "내 리뷰" 조회 API. {@code /api/users/me/**} 아래에 있지만 UserController가
 * 아니라 Review 도메인 소유 컨트롤러로 따로 둔다 - PetController가 {@code /api/users/me/pets}를
 * 별도 컨트롤러로 갖는 것과 같은 패턴이다.
 *
 * <p>수정·삭제는 이 컨트롤러에 따로 두지 않는다 - 기존 {@code /api/fairs/{fairId}/reviews/{reviewId}}
 * PATCH/DELETE(B.4)를 그대로 쓴다. 목록에 fairId가 같이 내려가므로 화면에서 바로 호출할 수 있다.
 */
@RestController
@RequestMapping("/api/users/me/reviews")
@RequiredArgsConstructor
public class MyReviewController {

    private final FairReviewQueryService fairReviewQueryService;

    @GetMapping
    public MyFairReviewListResponse list(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return fairReviewQueryService.getMyReviews(userId, page, size);
    }
}
