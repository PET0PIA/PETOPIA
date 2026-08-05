package com.ms.petopia.api.statistics.controller;

import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import com.ms.petopia.api.statistics.service.ReservationDashboardService;
import com.ms.petopia.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/fairs")
@RequiredArgsConstructor
public class ReservationDashboardController {
    private final ReservationDashboardService dashboardService;

    @GetMapping("/{fairId}/reservation-dashboard")
    public ResponseEntity<ApiResponse<List<ReservationDateSummaryDto>>> getDashboard(
            @PathVariable Long fairId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ){
        List<ReservationDateSummaryDto> result = dashboardService.getDateSummary(fairId, date);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
