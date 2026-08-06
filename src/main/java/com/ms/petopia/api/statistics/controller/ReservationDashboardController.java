package com.ms.petopia.api.statistics.controller;

import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import com.ms.petopia.api.statistics.service.ReservationDashboardService;
import com.ms.petopia.api.statistics.sse.DashboardEmitterRegistry;
import com.ms.petopia.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

    @GetMapping("/{fairId}/reservation-dashboard")
    public ResponseEntity<ApiResponse<List<ReservationDateSummaryDto>>> getDashboard(
            @PathVariable Long fairId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ){
        List<ReservationDateSummaryDto> result = dashboardService.getDateSummary(fairId, date);
        return ResponseEntity.ok(ApiResponse.success(result));
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
}
