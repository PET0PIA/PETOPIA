package com.ms.petopia.api.banner.controller;

import com.ms.petopia.api.banner.dto.request.BannerCreateRequest;
import com.ms.petopia.api.banner.dto.request.BannerOrderRequest;
import com.ms.petopia.api.banner.dto.request.BannerUpdateRequest;
import com.ms.petopia.api.banner.dto.response.BannerResponse;
import com.ms.petopia.api.banner.service.BannerService;
import com.ms.petopia.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.annotations.Param;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/banners")
@RequiredArgsConstructor
public class AdminBannerController {
    private final BannerService bannerService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(bannerService.getAll()));
    }

    @GetMapping("/{bannerId}")
    public ResponseEntity<ApiResponse<BannerResponse>> getById(@PathVariable Long bannerId){
        return ResponseEntity.ok(ApiResponse.success(bannerService.getById(bannerId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BannerResponse>> create(
            @AuthenticationPrincipal Long callerId,
            @Valid @RequestBody BannerCreateRequest request
    ) {
      return ResponseEntity.status(HttpStatus.CREATED)
              .body(ApiResponse.success(HttpStatus.CREATED, bannerService.create(callerId, request)));
    }

    @PutMapping("/{bannerId}")
    public ResponseEntity<ApiResponse<BannerResponse>> update(
            @PathVariable Long bannerId,
            @Valid @RequestBody BannerUpdateRequest request
    ){
        return ResponseEntity.ok(ApiResponse.success(bannerService.update(bannerId, request)));
    }

    @DeleteMapping("/{bannerId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long bannerId){
        bannerService.delete(bannerId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/{bannerId}/toggle")
    public ResponseEntity<ApiResponse<Void>> toggleActive(
            @PathVariable Long bannerId,
            @RequestParam boolean isActive
    ){
        bannerService.toggleActive(bannerId, isActive);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/order")
    public ResponseEntity<ApiResponse<Void>> updateOrder(
            @Valid @RequestBody BannerOrderRequest request
    ){
        bannerService.updateOrder(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
