package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.CreateFairApplicationRequest;
import com.ms.petopia.api.fair.dto.CreateFairApplicationResponse;
import com.ms.petopia.api.fair.dto.FairApplicationDetailResponse;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationRequest;
import com.ms.petopia.api.fair.dto.ReviewFairApplicationResponse;
import com.ms.petopia.api.fair.service.FairService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/fairs")
@RequiredArgsConstructor
public class FairController {

    private final FairService fairService;

    @PostMapping
    public ResponseEntity<CreateFairApplicationResponse> createApplication(
            @RequestHeader(FairTemporaryAuthHeaders.USER_ID) Long userId,
            @RequestBody CreateFairApplicationRequest request
    ) {
        // TODO 인증 도메인 완성 후 X-User-Id 대신 인증 Principal에서 userId를 가져온다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fairService.createApplication(userId, request));
    }

    @GetMapping("/{fairId}")
    public FairApplicationDetailResponse getApplication(@PathVariable Long fairId) {
        return fairService.getApplication(fairId);
    }

    @PatchMapping("/{fairId}/review")
    public ReviewFairApplicationResponse reviewApplication(
            @PathVariable Long fairId,
            @RequestHeader(FairTemporaryAuthHeaders.USER_ID) Long reviewerId,
            @RequestBody ReviewFairApplicationRequest request
    ) {
        // TODO 인증 도메인 완성 후 X-User-Id 대신 SUPER_ADMIN 인증 Principal에서 reviewerId를 가져온다.
        return fairService.review(fairId, reviewerId, request);
    }
}
