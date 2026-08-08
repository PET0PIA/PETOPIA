package com.ms.petopia.api.statistics.controller;

import com.ms.petopia.api.statistics.dto.*;
import com.ms.petopia.api.statistics.service.ReservationDashboardService;
import com.ms.petopia.api.statistics.service.VisitStatsExportService;
import com.ms.petopia.api.statistics.sse.DashboardEmitterRegistry;
import com.ms.petopia.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/fairs")
@RequiredArgsConstructor
public class ReservationDashboardController {
    private final ReservationDashboardService dashboardService;
    private final DashboardEmitterRegistry emitterRegistry;
    private final VisitStatsExportService exportService;

    @GetMapping("/{fairId}/reservation-dashboard")
    public ResponseEntity<ApiResponse<List<ReservationDateSummaryDto>>> getDashboard(
            @PathVariable Long fairId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ){
        List<ReservationDateSummaryDto> result = dashboardService.getDateSummary(fairId, date);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{fairId}/qr-issuance-summary")
    public ResponseEntity<ApiResponse<List<QrIssuanceSummaryDto>>> getQrIssuanceSummary(
            @PathVariable Long fairId
    ) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getQrIssuanceSummary(fairId)));
    }

    @GetMapping("/{fairId}/hourly-entry-trend")
    public ResponseEntity<ApiResponse<List<HourlyEntryTrendDto>>> getHourlyEntryTrend(
            @PathVariable Long fairId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getHourlyEntryTrend(fairId, date)));
    }

    @GetMapping("/{fairId}/booth-visit-stats")
    public ResponseEntity<ApiResponse<List<BoothVisitStatDto>>> getBoothVisitStats(
            @PathVariable Long fairId
    ) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getBoothVisitStats(fairId)));
    }

    @GetMapping("/{fairId}/booth-visit-pattern")
    public ResponseEntity<ApiResponse<List<LabelCountDto>>> getBoothVisitPattern(
            @PathVariable Long fairId
    ) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getBoothVisitPatternDistribution(fairId)));
    }

    // SSE 연결
    @GetMapping(value = "/{fairId}/reservation-dashboard/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE) // 브라우저가 EventSource로 인식하는 MIME타입
    public SseEmitter streamDashboard(@PathVariable Long fairId){
        SseEmitter emitter = emitterRegistry.register(fairId);

        // 연결 직후 현재 현황을 즉시 전송
        try{
            List<ReservationDateSummaryDto> initial = dashboardService.getDateSummary(fairId, null);
            emitter.send(SseEmitter.event()
                    .name("dashboard-update")
                    .data(initial, MediaType.APPLICATION_JSON)
            );
        }catch (IOException e){
            emitter.completeWithError(e);
        }
        return emitter;
    }

    @GetMapping("/{fairId}/visit-stats")
    public ResponseEntity<ApiResponse<VisitStatsDto>> getVisitStats(
            @PathVariable Long fairId
    ) {
        return ResponseEntity.ok(ApiResponse.success(dashboardService.getVisitStats(fairId)));
    }

    @GetMapping("/{fairId}/visit-stats/export")
    public ResponseEntity<byte[]> exportVisitStats(@PathVariable Long fairId) throws IOException {
        byte[] body = exportService.exportAsExcel(fairId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"visit-stats-" + fairId + ".xlsx\"")
                .header(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(body);
    }

}
