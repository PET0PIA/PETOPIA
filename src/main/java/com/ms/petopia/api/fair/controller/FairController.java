package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.CreateFairApplicationRequest;
import com.ms.petopia.api.fair.dto.CreateFairApplicationResponse;
import com.ms.petopia.api.fair.service.FairService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}
