package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.CreateFairCancelRequestRequest;
import com.ms.petopia.api.fair.dto.FairCancelRequestResponse;
import com.ms.petopia.api.fair.dto.ReviewFairCancelRequestRequest;
import com.ms.petopia.api.fair.dto.ReviewFairCancelRequestResponse;
import com.ms.petopia.api.fair.service.FairCancelRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 요청자 식별은 {@code @AuthenticationPrincipal}로 받는다. createCancelRequest는
 * SecurityConfig에서 EVENT_ADMIN role만, reviewCancelRequest는 SUPER_ADMIN role만
 * 통과하도록 막는다({@code SecurityConfig}의 "Fair 도메인" 섹션 참고).
 */
@RestController
@RequestMapping("/api/fairs/{fairId}/fair-cancel-requests")
@RequiredArgsConstructor
public class FairCancelRequestController {

    private final FairCancelRequestService cancelRequestService;

    @PostMapping
    public ResponseEntity<FairCancelRequestResponse> createCancelRequest(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long requestedBy,
            @RequestBody CreateFairCancelRequestRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(cancelRequestService.create(fairId, requestedBy, request));
    }

    @GetMapping
    public ResponseEntity<List<FairCancelRequestResponse>> getCancelRequests(@PathVariable Long fairId) {
        return ResponseEntity.ok(cancelRequestService.getCancelRequests(fairId));
    }

    @PatchMapping("/{cancelRequestId}/review")
    public ReviewFairCancelRequestResponse reviewCancelRequest(
            @PathVariable Long fairId,
            @PathVariable Long cancelRequestId,
            @AuthenticationPrincipal Long reviewerId,
            @RequestBody ReviewFairCancelRequestRequest request
    ) {
        // 승인 시 환불은 여기서 직접 트리거하지 않는다 - FairCancelRefundJob이 canceled_at이
        // 채워진 행사를 스스로 찾아 처리한다(FairCancelRefundOrchestrationService 클래스 주석
        // 참고, 결제 도메인 호출 실패가 이 API 응답에 영향을 주지 않게 하려는 목적).
        return cancelRequestService.review(fairId, cancelRequestId, reviewerId, request);
    }
}
