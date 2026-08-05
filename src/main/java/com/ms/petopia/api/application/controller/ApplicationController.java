package com.ms.petopia.api.application.controller;

import com.ms.petopia.api.application.dto.response.BoothSlotLockStatusResponse;
import com.ms.petopia.api.application.service.ApplicationService;
import com.ms.petopia.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
