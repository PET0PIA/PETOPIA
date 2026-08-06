package com.ms.petopia.api.statistics.controller;

import com.ms.petopia.api.statistics.dto.BoothVisitStatDto;
import com.ms.petopia.api.statistics.dto.HourlyEntryTrendDto;
import com.ms.petopia.api.statistics.dto.QrIssuanceSummaryDto;
import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import com.ms.petopia.api.statistics.service.ReservationDashboardService;
import com.ms.petopia.api.statistics.sse.DashboardEmitterRegistry;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ReservationDashboardController.class)
class ReservationDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReservationDashboardService dashboardService;

    @MockitoBean
    private DashboardEmitterRegistry emitterRegistry;

    // SecurityConfig → JwtAuthenticationFilter → JwtTokenProvider 의존성 체인을 끊기 위해 등록
    // anyRequest().permitAll() 설정으로 인증 없이 테스트 요청이 통과된다
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // ── reservation-dashboard ─────────────────────────────────────────

    @Test
    void getDashboard_noDateParam_returnsAllDates() throws Exception {
        List<ReservationDateSummaryDto> fakeResult = List.of(
                makeSummary(LocalDate.of(2026, 8, 1), 200, 150),
                makeSummary(LocalDate.of(2026, 8, 2), 200, 80)
        );
        given(dashboardService.getDateSummary(eq(1L), isNull()))
                .willReturn(fakeResult);

        mockMvc.perform(get("/api/fairs/1/reservation-dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].operationDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data[0].capacity").value(200));
    }

    @Test
    void getDashboard_withDateParam_returnsFilteredDate() throws Exception {
        List<ReservationDateSummaryDto> fakeResult = List.of(
                makeSummary(LocalDate.of(2026, 8, 1), 200, 150)
        );
        given(dashboardService.getDateSummary(eq(1L), eq(LocalDate.of(2026, 8, 1))))
                .willReturn(fakeResult);

        mockMvc.perform(get("/api/fairs/1/reservation-dashboard")
                        .param("date", "2026-08-01")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].operationDate").value("2026-08-01"));
    }

    @Test
    void getDashboard_invalidDateFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/fairs/1/reservation-dashboard")
                        .param("date", "20260801"))
                .andExpect(status().isBadRequest());
    }

    // ── qr-issuance-summary ───────────────────────────────────────────

    @Test
    void getQrIssuanceSummary_returnsList() throws Exception {
        QrIssuanceSummaryDto dto1 = makeQrSummary(LocalDate.of(2026, 8, 1), 120, 100, 20);
        QrIssuanceSummaryDto dto2 = makeQrSummary(LocalDate.of(2026, 8, 2), 80,  75,  5);
        given(dashboardService.getQrIssuanceSummary(1L)).willReturn(List.of(dto1, dto2));

        mockMvc.perform(get("/api/fairs/1/qr-issuance-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].operationDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data[0].qrIssuedCount").value(120))
                .andExpect(jsonPath("$.data[0].qrActiveCount").value(100))
                .andExpect(jsonPath("$.data[0].qrRevokedCount").value(20));
    }

    @Test
    void getQrIssuanceSummary_noData_returnsEmptyList() throws Exception {
        given(dashboardService.getQrIssuanceSummary(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/fairs/1/qr-issuance-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── hourly-entry-trend ────────────────────────────────────────────

    @Test
    void getHourlyEntryTrend_returnsHourlyList() throws Exception {
        HourlyEntryTrendDto hour10 = makeHourlyDto(10, 30);
        HourlyEntryTrendDto hour11 = makeHourlyDto(11, 55);
        given(dashboardService.getHourlyEntryTrend(eq(1L), eq(LocalDate.of(2026, 8, 1))))
                .willReturn(List.of(hour10, hour11));

        mockMvc.perform(get("/api/fairs/1/hourly-entry-trend")
                        .param("date", "2026-08-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].entryHour").value(10))
                .andExpect(jsonPath("$.data[0].entryCount").value(30))
                .andExpect(jsonPath("$.data[1].entryHour").value(11))
                .andExpect(jsonPath("$.data[1].entryCount").value(55));
    }

    @Test
    void getHourlyEntryTrend_missingDateParam_returns400() throws Exception {
        // date는 @RequestParam 필수값이므로 누락 시 400
        mockMvc.perform(get("/api/fairs/1/hourly-entry-trend"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getHourlyEntryTrend_invalidDateFormat_returns400() throws Exception {
        mockMvc.perform(get("/api/fairs/1/hourly-entry-trend")
                        .param("date", "20260801"))
                .andExpect(status().isBadRequest());
    }

    // ── booth-visit-stats ─────────────────────────────────────────────

    @Test
    void getBoothVisitStats_returnsList() throws Exception {
        BoothVisitStatDto dto1 = makeBoothDto(1L, "A-01", "펫샵 강남", 200, 210);
        BoothVisitStatDto dto2 = makeBoothDto(2L, "A-02", "고양이 왕국", 150, 155);
        given(dashboardService.getBoothVisitStats(1L)).willReturn(List.of(dto1, dto2));

        mockMvc.perform(get("/api/fairs/1/booth-visit-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].boothNumber").value("A-01"))
                .andExpect(jsonPath("$.data[0].boothName").value("펫샵 강남"))
                .andExpect(jsonPath("$.data[0].uniqueVisitorCount").value(200))
                .andExpect(jsonPath("$.data[0].totalScanCount").value(210));
    }

    @Test
    void getBoothVisitStats_noData_returnsEmptyList() throws Exception {
        given(dashboardService.getBoothVisitStats(1L)).willReturn(List.of());

        mockMvc.perform(get("/api/fairs/1/booth-visit-stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ── 헬퍼 메서드 ──────────────────────────────────────────────────

    private ReservationDateSummaryDto makeSummary(LocalDate date, int capacity, int confirmed) {
        ReservationDateSummaryDto dto = new ReservationDateSummaryDto();
        dto.setOperationDate(date);
        dto.setCapacity(capacity);
        dto.setConfirmedCount(confirmed);
        dto.setRemainingCapacity(capacity - confirmed);
        return dto;
    }

    private QrIssuanceSummaryDto makeQrSummary(LocalDate date, int issued, int active, int revoked) {
        QrIssuanceSummaryDto dto = new QrIssuanceSummaryDto();
        dto.setOperationDate(date);
        dto.setQrIssuedCount(issued);
        dto.setQrActiveCount(active);
        dto.setQrRevokedCount(revoked);
        return dto;
    }

    private HourlyEntryTrendDto makeHourlyDto(int hour, int count) {
        HourlyEntryTrendDto dto = new HourlyEntryTrendDto();
        dto.setEntryHour(hour);
        dto.setEntryCount(count);
        return dto;
    }

    private BoothVisitStatDto makeBoothDto(Long boothId, String number, String name,
                                           int uniqueCount, int totalCount) {
        BoothVisitStatDto dto = new BoothVisitStatDto();
        dto.setBoothId(boothId);
        dto.setBoothNumber(number);
        dto.setBoothName(name);
        dto.setUniqueVisitorCount(uniqueCount);
        dto.setTotalScanCount(totalCount);
        return dto;
    }
}
