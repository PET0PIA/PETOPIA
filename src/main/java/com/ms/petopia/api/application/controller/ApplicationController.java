package com.ms.petopia.api.application.controller;

import com.ms.petopia.api.application.dto.request.ApplicationApproveRequest;
import com.ms.petopia.api.application.dto.request.ApplicationCancelRequestSubmitRequest;
import com.ms.petopia.api.application.dto.request.ApplicationRejectRequest;
import com.ms.petopia.api.application.dto.request.ApplicationSubmitRequest;
import com.ms.petopia.api.application.dto.response.*;
import com.ms.petopia.api.application.service.ApplicationService;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApplicationController {

    private final ApplicationService applicationService;

    // 부스 슬롯 목록 + 잠금 상태 조회 (비회원 포함 공개, 인증 불필요)
    @GetMapping("/fairs/{fairId}/booth-slots")
    public ResponseEntity<ApiResponse<List<BoothSlotLockStatusResponse>>> getBoothSlots(@PathVariable Long fairId) {

        return ResponseEntity.ok(
                ApiResponse.success(applicationService.getBoothSlots(fairId)));

    }

    // 참가 신청서 제출
    @PostMapping("/fairs/{fairId}/applications")
    public ResponseEntity<ApiResponse<ApplicationResponse>> submitApplication(
            @AuthenticationPrincipal Long ownerId,
            @PathVariable Long fairId,
            @Valid @RequestBody ApplicationSubmitRequest request
    ) {

        ApplicationResponse response = applicationService.submitApplication(ownerId, fairId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, response));

    }

    // 내 신청 현황 목록 조회
    @GetMapping("/applications")
    public ResponseEntity<ApiResponse<List<ApplicationSummaryResponse>>> getMyApplications(
            @AuthenticationPrincipal Long ownerId,
            @RequestParam(required = false) Long businessId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(applicationService.getMyApplications(ownerId, businessId)));

    }

    // 신청 상세 조회 (사업자 본인 또는 담당 행사 관리자 조회 가능)
    @GetMapping("/applications/{applicationId}")
    public ResponseEntity<ApiResponse<ApplicationDetailResponse>> getApplicationDetail(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long applicationId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(applicationService.getApplicationDetail(userId, applicationId)));

    }

    // 담당 행사의 신청 목록 조회 (행사 담당자용)
    @GetMapping("/fairs/{fairId}/applications")
    public ResponseEntity<ApiResponse<List<ApplicationReviewSummaryResponse>>> getApplicationsForFair(
            @PathVariable Long fairId,
            @RequestParam(required = false) String status
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(applicationService.getApplicationsForFair(fairId, status)));

    }

    // 참가 신청서 승인 (행사 담당자용)
    @PutMapping("/applications/{applicationId}/approve")
    public ResponseEntity<ApiResponse<ApplicationReviewResultResponse>> approveApplication(
            @PathVariable Long applicationId,
            @Valid @RequestBody(required = false) ApplicationApproveRequest request
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(applicationService.approveApplication(applicationId, request)));

    }

    // 참가 신청서 반려 (행사 담당자용)
    @PutMapping("/applications/{applicationId}/reject")
    public ResponseEntity<ApiResponse<ApplicationReviewResultResponse>> rejectApplication(
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationRejectRequest request
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(applicationService.rejectApplication(applicationId, request)));

    }

    // 담당 행사의 취소 요청 목록 조회 (행사 담당자용)
    @GetMapping("/fairs/{fairId}/cancel-requests")
    public ResponseEntity<ApiResponse<List<ApplicationCancelRequestSummaryResponse>>> getCancelRequestsForFair(
            @PathVariable Long fairId,
            @RequestParam(required = false) String status
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(applicationService.getCancelRequestsForFair(fairId, status)));

    }

    // 참가 취소 요청 제출
    @PostMapping("/applications/{applicationId}/cancel-requests")
    public ResponseEntity<ApiResponse<Void>> submitCancelRequest(
            @AuthenticationPrincipal Long ownerId,
            @PathVariable Long applicationId,
            @Valid @RequestBody ApplicationCancelRequestSubmitRequest request
    ) {

        applicationService.submitCancelRequest(ownerId, applicationId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, null));

    }

    // 참가 취소 요청 승인 (행사 담당자용)
    @PutMapping("/applications/{applicationId}/cancel-requests/approve")
    public ResponseEntity<ApiResponse<ApplicationCancelRequestResultResponse>> approveCancelRequest(
            @AuthenticationPrincipal Long adminUserId,
            @PathVariable Long applicationId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(applicationService.approveCancelRequest(adminUserId, applicationId)));

    }

    // 참가 취소 요청 반려 (행사 담당자용)
    @PutMapping("/applications/{applicationId}/cancel-requests/reject")
    public ResponseEntity<ApiResponse<ApplicationCancelRequestResultResponse>> rejectCancelRequest(
            @PathVariable Long applicationId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(applicationService.rejectCancelRequest(applicationId)));

    }

}
