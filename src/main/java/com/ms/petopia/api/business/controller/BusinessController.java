package com.ms.petopia.api.business.controller;

import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.dto.request.BusinessRegisterRequest;
import com.ms.petopia.api.business.dto.response.BusinessResponse;
import com.ms.petopia.api.business.dto.response.BusinessReviewDetailResponse;
import com.ms.petopia.api.business.dto.response.BusinessReviewResultResponse;
import com.ms.petopia.api.business.dto.response.BusinessReviewSummaryResponse;
import com.ms.petopia.api.business.service.BusinessService;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/businesses")
@RequiredArgsConstructor
public class BusinessController {

    private final BusinessService businessService;

    // 사업자 등록 및 진위 확인
    @PostMapping
    public ResponseEntity<ApiResponse<BusinessResponse>> register(
            @AuthenticationPrincipal Long ownerId,
            @Valid @RequestBody BusinessRegisterRequest request
    ) {

        BusinessResponse response = businessService.registerBusiness(ownerId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.success(HttpStatus.CREATED, response));

    }

    // 내 사업자 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<BusinessResponse>>> getMyBusinesses(
            @AuthenticationPrincipal Long ownerId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(businessService.getMyBusinesses(ownerId)));

    }

    // 사업자 상세 조회 (진위 확인 상태 포함)
    @GetMapping("/{businessId}")
    public ResponseEntity<ApiResponse<BusinessResponse>> getBusiness(
            @AuthenticationPrincipal Long ownerId,
            @PathVariable Long businessId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(businessService.getBusiness(ownerId, businessId)));

    }

    // 심사 상태별 사업자 목록 조회 (관리자용, 기본값 심사 대기중)
    @GetMapping("/review")
    public ResponseEntity<ApiResponse<List<BusinessReviewSummaryResponse>>> getBusinessesForReview(
            @RequestParam(defaultValue = "PENDING_REVIEW") Business.ApprovalStatus status
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(businessService.getBusinessesForReview(status)));

    }

    // 사업자 심사 상세 조회 (관리자용, 소유자 체크 없음)
    @GetMapping("/{businessId}/review")
    public ResponseEntity<ApiResponse<BusinessReviewDetailResponse>> getBusinessReviewDetail(
            @PathVariable Long businessId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(businessService.getBusinessReviewDetail(businessId)));

    }

    // 사업자 승인 (관리자용)
    @PatchMapping("/{businessId}/approve")
    public ResponseEntity<ApiResponse<BusinessReviewResultResponse>> approveBusiness(
            @AuthenticationPrincipal Long reviewerId,
            @PathVariable Long businessId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(businessService.approveBusiness(reviewerId, businessId)));

    }

}
