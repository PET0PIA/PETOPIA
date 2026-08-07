package com.ms.petopia.api.statistics.controller;

import com.ms.petopia.api.statistics.dto.AdminDashboardSummaryDto;
import com.ms.petopia.api.statistics.dto.FairSummaryDto;
import com.ms.petopia.api.statistics.service.AdminDashboardService;
import com.ms.petopia.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminDashboardController {
    private final AdminDashboardService adminDashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<AdminDashboardSummaryDto>> getDashboard(){
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getDashboardSummary()));
    }

    @GetMapping("/dashboard/fairs")
    public ResponseEntity<ApiResponse<List<FairSummaryDto>>> getFairSummaryList(){
        return ResponseEntity.ok(ApiResponse.success(adminDashboardService.getFairSummaryList()));
    }
}
