package com.ms.petopia.api.booth.controller;

import com.ms.petopia.api.booth.dto.request.BoothItemCreateRequest;
import com.ms.petopia.api.booth.dto.request.BoothItemUpdateRequest;
import com.ms.petopia.api.booth.dto.request.BoothUpdateRequest;
import com.ms.petopia.api.booth.dto.response.BoothFavoriteResponse;
import com.ms.petopia.api.booth.dto.response.BoothItemResponse;
import com.ms.petopia.api.booth.dto.response.BoothResponse;
import com.ms.petopia.api.booth.dto.response.ConfirmedBoothResponse;
import com.ms.petopia.api.booth.service.BoothService;
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
public class BoothController {

    private final BoothService boothService;

    // 부스 상세 조회 (비회원 포함 공개, 인증 불필요)
    @GetMapping("/booths/{boothId}")
    public ResponseEntity<ApiResponse<BoothResponse>> getBooth(
            @AuthenticationPrincipal Long viewerId,
            @PathVariable Long boothId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(boothService.getBooth(boothId, viewerId)));

    }

    // 부스 프로필 수정 (본인 소유만)
    @PutMapping("/booths/{boothId}")
    public ResponseEntity<ApiResponse<BoothResponse>> updateBooth(
            @AuthenticationPrincipal Long callerId,
            @PathVariable Long boothId,
            @Valid @RequestBody BoothUpdateRequest request
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(boothService.updateBooth(callerId, boothId, request)));

    }

    // 내 즐겨찾기 목록 조회
    @GetMapping("/booths/favorites")
    public ResponseEntity<ApiResponse<List<BoothFavoriteResponse>>> getMyFavorites(
            @AuthenticationPrincipal Long callerId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(boothService.getMyFavorites(callerId)));

    }

    // 판매상품·이벤트 등록 (본인 소유 부스만)
    @PostMapping("/booths/{boothId}/items")
    public ResponseEntity<ApiResponse<BoothItemResponse>> addItem(
            @AuthenticationPrincipal Long callerId,
            @PathVariable Long boothId,
            @Valid @RequestBody BoothItemCreateRequest request
    ) {

        BoothItemResponse response = boothService.addItem(callerId, boothId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(HttpStatus.CREATED, response));

    }

    // 판매상품·이벤트 수정 (본인 소유 부스만)
    @PutMapping("/booth-items/{boothItemId}")
    public ResponseEntity<ApiResponse<BoothItemResponse>> updateItem(
            @AuthenticationPrincipal Long callerId,
            @PathVariable Long boothItemId,
            @Valid @RequestBody BoothItemUpdateRequest request
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(boothService.updateItem(callerId, boothItemId, request)));

    }

    // 판매상품·이벤트 삭제 (본인 소유 부스만)
    @DeleteMapping("/booth-items/{boothItemId}")
    public ResponseEntity<ApiResponse<Void>> deleteItem(
            @AuthenticationPrincipal Long callerId,
            @PathVariable Long boothItemId
    ) {

        boothService.deleteItem(callerId, boothItemId);

        return ResponseEntity.ok(
                ApiResponse.success(null));

    }

    // 확정 부스 안내판 조회 (비회원 포함 공개, 인증 불필요)
    @GetMapping("/fairs/{fairId}/confirmed-booths")
    public ResponseEntity<ApiResponse<List<ConfirmedBoothResponse>>> getConfirmedBooths(
            @PathVariable Long fairId
    ) {

        return ResponseEntity.ok(
                ApiResponse.success(boothService.getConfirmedBooths(fairId)));

    }

}
