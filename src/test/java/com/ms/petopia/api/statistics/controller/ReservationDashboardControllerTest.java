package com.ms.petopia.api.statistics.controller;

import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import com.ms.petopia.api.statistics.service.ReservationDashboardService;
import com.ms.petopia.api.statistics.sse.DashboardEmitterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
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

    @Test
    void getDashboard_noDateParam_returnsAllDates() throws Exception {
        // given
        List<ReservationDateSummaryDto> fakeResult = List.of(
                makeSummary(LocalDate.of(2026, 8, 1), 200, 150),
                makeSummary(LocalDate.of(2026, 8, 2), 200, 80)
        );
        given(dashboardService.getDateSummary(eq(1L), isNull()))
                .willReturn(fakeResult);

        // when & then
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
        // given
        List<ReservationDateSummaryDto> fakeResult = List.of(
                makeSummary(LocalDate.of(2026, 8, 1), 200, 150)
        );
        given(dashboardService.getDateSummary(eq(1L), eq(LocalDate.of(2026, 8, 1))))
                .willReturn(fakeResult);

        // when & then
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
        // "20260801" 은 ISO 포맷(yyyy-MM-dd)이 아니므로 @DateTimeFormat 변환 실패 → 400
        mockMvc.perform(get("/api/fairs/1/reservation-dashboard")
                        .param("date", "20260801"))
                .andExpect(status().isBadRequest());
    }

    private ReservationDateSummaryDto makeSummary(LocalDate date, int capacity, int confirmed) {
        ReservationDateSummaryDto dto = new ReservationDateSummaryDto();
        dto.setOperationDate(date);
        dto.setCapacity(capacity);
        dto.setConfirmedCount(confirmed);
        dto.setRemainingCapacity(capacity - confirmed);
        return dto;
    }
}
