package com.ms.petopia.api.review.controller;

import com.ms.petopia.api.review.dto.FairReviewStatsResponse;
import com.ms.petopia.api.review.service.FairReviewStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사관리자 통계 - 리뷰(태그) 데이터 기반 부분만 담당한다. 예약·입장 데이터 기반 부분은
 * com.ms.petopia.api.statistics 도메인(meltingujin)의 기존 API를 그대로 쓴다 - 프론트
 * (Phase 6)가 두 API를 함께 호출해서 한 화면에 합친다.
 */
@RestController
@RequestMapping("/api/fairs/{fairId}/reviews/stats")
@RequiredArgsConstructor
public class FairReviewStatsController {

    private final FairReviewStatsService fairReviewStatsService;

    @GetMapping
    public FairReviewStatsResponse stats(@PathVariable Long fairId) {
        return fairReviewStatsService.getStats(fairId);
    }
}
