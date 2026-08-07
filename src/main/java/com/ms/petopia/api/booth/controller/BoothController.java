package com.ms.petopia.api.booth.controller;

import com.ms.petopia.api.booth.dto.response.BoothResponse;
import com.ms.petopia.api.booth.service.BoothService;
import com.ms.petopia.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BoothController {

    private final BoothService boothService;

    @GetMapping("/booths/{boothId}")
    public ResponseEntity<ApiResponse<BoothResponse>> getBooth(@PathVariable Long boothId) {

        return ResponseEntity.ok(
                ApiResponse.success(boothService.getBooth(boothId)));

    }

}
