package com.ms.petopia.api.booth.controller;

import com.ms.petopia.api.booth.dto.request.BoothItemCreateRequest;
import com.ms.petopia.api.booth.dto.request.BoothUpdateRequest;
import com.ms.petopia.api.booth.dto.response.BoothItemResponse;
import com.ms.petopia.api.booth.dto.response.BoothResponse;
import com.ms.petopia.api.booth.service.BoothService;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoothController {

    private final BoothService boothService;

    // 부스 상세 조회 (비회원 포함 공개, 인증 불필요)
    @GetMapping("/booths/{boothId}")
    public ResponseEntity<ApiResponse<BoothResponse>> getBooth(@PathVariable Long boothId) {

        return ResponseEntity.ok(
                ApiResponse.success(boothService.getBooth(boothId)));

    }

    // 부스 프로필 수정 (본인 소유만)
    @PutMapping("/booths/{boothId}")
    public ResponseEntity<ApiResponse<BoothResponse>> updateBooth(
            @RequestHeader(BoothTemporaryAuthHeaders.USER_ID) Long callerId,
            @PathVariable Long boothId,
            @Valid @RequestBody BoothUpdateRequest request
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(boothService.updateBooth(callerId, boothId, request)));

    }

    // 판매상품·이벤트 등록 (본인 소유 부스만)
    @PostMapping("/booths/{boothId}/items")
    public ResponseEntity<ApiResponse<BoothItemResponse>> addItem(
            @RequestHeader(BoothTemporaryAuthHeaders.USER_ID) Long callerId,
            @PathVariable Long boothId,
            @Valid @RequestBody BoothItemCreateRequest request
    ) {

        BoothItemResponse response = boothService.addItem(callerId, boothId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, response));

    }

}
