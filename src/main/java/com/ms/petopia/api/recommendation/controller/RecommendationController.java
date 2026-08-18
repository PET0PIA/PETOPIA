package com.ms.petopia.api.recommendation.controller;

import com.ms.petopia.api.recommendation.dto.BoothRecommendationItem;
import com.ms.petopia.api.recommendation.dto.BoothRecommendationRequest;
import com.ms.petopia.api.recommendation.dto.HallRoute;
import com.ms.petopia.api.recommendation.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fairs")
public class RecommendationController {

    private final RecommendationService recommendationService;

    //클로드 부스 추천
    @PostMapping("/{fairId}/booth-recommendations")
    public List<BoothRecommendationItem> recommend(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BoothRecommendationRequest request
    ) {
        return recommendationService.recommend(fairId, userId, request);
    }

    //클로드 동선 추천
    @PostMapping("/{fairId}/booth-routes")
    public List<HallRoute> recommendRoute(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody BoothRecommendationRequest request
    ) {
        return recommendationService.recommendRoute(fairId, userId, request);
    }
}
