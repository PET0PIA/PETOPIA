package com.ms.petopia.api.banner.controller;

import com.ms.petopia.api.banner.dto.response.BannerResponse;
import com.ms.petopia.api.banner.service.BannerService;
import com.ms.petopia.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/banners")
@RequiredArgsConstructor
public class BannerController {
    private final BannerService bannerService;

    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<BannerResponse>>> getActiveList(){
        return ResponseEntity.ok(ApiResponse.success(bannerService.getActiveList()));
    }
}
