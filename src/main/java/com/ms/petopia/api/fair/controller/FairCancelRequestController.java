package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.CreateFairCancelRequestRequest;
import com.ms.petopia.api.fair.dto.FairCancelRequestResponse;
import com.ms.petopia.api.fair.dto.ReviewFairCancelRequestRequest;
import com.ms.petopia.api.fair.dto.ReviewFairCancelRequestResponse;
import com.ms.petopia.api.fair.service.FairCancelRefundOrchestrationService;
import com.ms.petopia.api.fair.service.FairCancelRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fairs/{fairId}/fair-cancel-requests")
@RequiredArgsConstructor
public class FairCancelRequestController {

    private final FairCancelRequestService cancelRequestService;
    private final FairCancelRefundOrchestrationService refundOrchestrationService;

    @PostMapping
    public ResponseEntity<FairCancelRequestResponse> createCancelRequest(
            @PathVariable Long fairId,
            @RequestHeader(FairTemporaryAuthHeaders.USER_ID) Long requestedBy,
            @RequestBody CreateFairCancelRequestRequest request
    ) {
        // TODO 인증 도메인 완성 후 X-User-Id 대신 EVENT_ADMIN 인증 Principal에서 requestedBy를 가져온다.
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
            @RequestHeader(FairTemporaryAuthHeaders.USER_ID) Long reviewerId,
            @RequestBody ReviewFairCancelRequestRequest request
    ) {
        // TODO 인증 도메인 완성 후 X-User-Id 대신 SUPER_ADMIN 인증 Principal에서 reviewerId를 가져온다.
        ReviewFairCancelRequestResponse response =
                cancelRequestService.review(fairId, cancelRequestId, reviewerId, request);
        if (response.canceledAt() != null) {
            // review()는 자체 트랜잭션이라 이 시점엔 이미 커밋돼 있다(프록시 메서드가 반환한 뒤라서).
            // 취소 승인이 실제로 반영된 뒤에만 환불을 내보내려고 일부러 review()와 분리된
            // 트랜잭션으로 호출한다(FairCancelRefundOrchestrationService 클래스 주석 참고).
            refundOrchestrationService.refundForCanceledFair(fairId, reviewerId);
        }
        return response;
    }
}
