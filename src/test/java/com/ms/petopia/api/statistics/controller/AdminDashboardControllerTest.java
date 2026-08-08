package com.ms.petopia.api.statistics.controller;

import com.ms.petopia.api.statistics.dto.AdminDashboardSummaryDto;
import com.ms.petopia.api.statistics.dto.FairSummaryDto;
import com.ms.petopia.api.statistics.service.AdminDashboardService;
import com.ms.petopia.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminDashboardController.class)
class AdminDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AdminDashboardService adminDashboardService;

    // SecurityConfig → JwtAuthenticationFilter → JwtTokenProvider 의존성 체인을 끊기 위해 등록
    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    // GET /api/admin/dashboard

    @Test
    @DisplayName("전체 통합 집계 - 정상 반환")
    void getDashboard_returnsSummary() throws Exception {
        AdminDashboardSummaryDto dto = makeSummary(5, 1, 2, 2, 300, 240);
        given(adminDashboardService.getDashboardSummary()).willReturn(dto);

        mockMvc.perform(get("/api/admin/dashboard")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalFairs").value(5))
                .andExpect(jsonPath("$.data.inProgressFairs").value(1))
                .andExpect(jsonPath("$.data.preparingFairs").value(2))
                .andExpect(jsonPath("$.data.endedFairs").value(2))
                .andExpect(jsonPath("$.data.totalReservations").value(300))
                .andExpect(jsonPath("$.data.totalVisitors").value(240));
    }

    @Test
    @DisplayName("전체 통합 집계 - 모든 값이 0이어도 정상 반환")
    void getDashboard_allZeros_returnsZeroValues() throws Exception {
        given(adminDashboardService.getDashboardSummary()).willReturn(makeSummary(0, 0, 0, 0, 0, 0));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalFairs").value(0))
                .andExpect(jsonPath("$.data.totalVisitors").value(0));
    }

    // GET /api/admin/dashboard/fairs

    @Test
    @DisplayName("행사별 요약 - 목록 정상 반환")
    void getFairSummaryList_returnsList() throws Exception {
        FairSummaryDto fair1 = makeFairSummary(1L, "펫페스타 2026", "IN_PROGRESS", 100, 80);
        FairSummaryDto fair2 = makeFairSummary(2L, "고양이 박람회", "ENDED", 200, 190);
        given(adminDashboardService.getFairSummaryList()).willReturn(List.of(fair1, fair2));

        mockMvc.perform(get("/api/admin/dashboard/fairs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].fairId").value(1))
                .andExpect(jsonPath("$.data[0].fairName").value("펫페스타 2026"))
                .andExpect(jsonPath("$.data[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data[0].totalReservations").value(100))
                .andExpect(jsonPath("$.data[0].totalVisitors").value(80))
                .andExpect(jsonPath("$.data[1].fairName").value("고양이 박람회"));
    }

    @Test
    @DisplayName("행사별 요약 - 운영 행사가 없으면 빈 배열 반환")
    void getFairSummaryList_noFairs_returnsEmptyArray() throws Exception {
        given(adminDashboardService.getFairSummaryList()).willReturn(List.of());

        mockMvc.perform(get("/api/admin/dashboard/fairs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("행사별 요약 - 운영일 필드가 올바른 날짜 형식으로 직렬화")
    void getFairSummaryList_dateFieldsSerializedCorrectly() throws Exception {
        FairSummaryDto fair = makeFairSummary(1L, "펫페스타 2026", "PREPARING", 50, 0);
        given(adminDashboardService.getFairSummaryList()).willReturn(List.of(fair));

        mockMvc.perform(get("/api/admin/dashboard/fairs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].operationStartDate").value("2026-08-01"))
                .andExpect(jsonPath("$.data[0].operationEndDate").value("2026-08-03"));
    }

    // 헬퍼 메서드

    private AdminDashboardSummaryDto makeSummary(int total, int inProgress, int preparing,
                                                  int ended, int reservations, int visitors) {
        AdminDashboardSummaryDto dto = new AdminDashboardSummaryDto();
        dto.setTotalFairs(total);
        dto.setInProgressFairs(inProgress);
        dto.setPreparingFairs(preparing);
        dto.setEndedFairs(ended);
        dto.setTotalReservations(reservations);
        dto.setTotalVisitors(visitors);
        return dto;
    }

    private FairSummaryDto makeFairSummary(Long id, String name, String status,
                                            int reservations, int visitors) {
        FairSummaryDto dto = new FairSummaryDto();
        dto.setFairId(id);
        dto.setFairName(name);
        dto.setStatus(status);
        dto.setOperationStartDate(LocalDate.of(2026, 8, 1));
        dto.setOperationEndDate(LocalDate.of(2026, 8, 3));
        dto.setTotalReservations(reservations);
        dto.setTotalVisitors(visitors);
        return dto;
    }
}
