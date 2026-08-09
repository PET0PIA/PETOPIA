package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.CreateFairApplicationRequest;
import com.ms.petopia.api.fair.dto.CreateFairApplicationResponse;
import com.ms.petopia.api.fair.dto.FairApplicationDetailResponse;
import com.ms.petopia.api.fair.dto.FairApplicationSummaryResponse;
import com.ms.petopia.api.fair.dto.FairPublicSummaryResponse;
import com.ms.petopia.api.fair.dto.PublishFairResponse;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationRequest;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationResponse;
import com.ms.petopia.api.fair.dto.UpdateFairApplicationRequest;
import com.ms.petopia.api.fair.service.FairService;
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

import java.util.List;

/**
 * 요청자 식별은 전부 {@code @AuthenticationPrincipal}(JwtAuthenticationFilter가 심어주는 userId)로
 * 받는다. review/publish/getApplication은 SecurityConfig에서 SUPER_ADMIN role로, getPublicSummary는
 * 인증 없이 permitAll로, 나머지는 로그인 여부만 검증한다({@code SecurityConfig}의 "Fair 도메인"
 * 섹션 참고) - 본인 신청 여부처럼 role만으로 못 가리는 검증은 지금처럼 서비스 계층(FairService)이
 * 계속 담당한다.
 */
@RestController
@RequestMapping("/api/fairs")
@RequiredArgsConstructor
public class FairController {

    private final FairService fairService;

    @PostMapping
    public ResponseEntity<CreateFairApplicationResponse> createApplication(
            @AuthenticationPrincipal Long userId,
            @RequestBody CreateFairApplicationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fairService.createApplication(userId, request));
    }

    @GetMapping("/mine")
    public List<FairApplicationSummaryResponse> getMyApplications(
            @AuthenticationPrincipal Long requesterId
    ) {
        return fairService.getMyApplications(requesterId);
    }

    @GetMapping("/{fairId}/mine")
    public FairApplicationDetailResponse getMyApplicationDetail(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long requesterId
    ) {
        return fairService.getMyApplicationDetail(fairId, requesterId);
    }

    @GetMapping("/{fairId}/public")
    public FairPublicSummaryResponse getPublicSummary(@PathVariable Long fairId) {
        // SecurityConfig에서 이 경로는 인증 없이 permitAll이다 - 티켓 예매 화면처럼 로그인
        // 여부와 무관하게 볼 수 있어야 하는 화면 전용(PII·심사 정보는 응답에 없음).
        return fairService.getPublicSummary(fairId);
    }

    @GetMapping("/{fairId}")
    public FairApplicationDetailResponse getApplication(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long requesterId
    ) {
        // SecurityConfig에서 SUPER_ADMIN role만 이 엔드포인트에 도달할 수 있게 막는다
        // (관리자 검토 화면 전용, managerPhone/managerEmail 노출).
        return fairService.getApplication(fairId, requesterId);
    }

    @PatchMapping("/{fairId}")
    public FairApplicationDetailResponse updateApplication(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long requesterId,
            @RequestBody UpdateFairApplicationRequest request
    ) {
        return fairService.updateApplication(fairId, requesterId, request);
    }

    @PatchMapping("/{fairId}/review")
    public ReviewFairApplicationResponse reviewApplication(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long reviewerId,
            @RequestBody ReviewFairApplicationRequest request
    ) {
        // SecurityConfig에서 SUPER_ADMIN role만 이 엔드포인트에 도달할 수 있게 막는다.
        return fairService.review(fairId, reviewerId, request);
    }

    @PatchMapping("/{fairId}/publish")
    public PublishFairResponse publish(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long actorId
    ) {
        // SecurityConfig에서 SUPER_ADMIN role만 이 엔드포인트에 도달할 수 있게 막는다.
        return fairService.publish(fairId, actorId);
    }
}
