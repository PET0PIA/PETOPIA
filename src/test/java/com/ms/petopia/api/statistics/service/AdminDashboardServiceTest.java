package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.statistics.dto.AdminDashboardSummaryDto;
import com.ms.petopia.api.statistics.dto.FairSummaryDto;
import com.ms.petopia.api.statistics.mapper.AdminDashboardMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private AdminDashboardMapper adminDashboardMapper;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    // getDashboardSummary

    @Test
    @DisplayName("Mapper 결과를 그대로 반환")
    void getDashboardSummary_returnsMapperResult() {
        AdminDashboardSummaryDto dto = makeSummary(5, 1, 2, 2, 300, 240);
        given(adminDashboardMapper.selectDashboardSummary()).willReturn(dto);

        AdminDashboardSummaryDto result = adminDashboardService.getDashboardSummary();

        assertThat(result.getTotalFairs()).isEqualTo(5);
        assertThat(result.getInProgressFairs()).isEqualTo(1);
        assertThat(result.getPreparingFairs()).isEqualTo(2);
        assertThat(result.getEndedFairs()).isEqualTo(2);
        assertThat(result.getTotalReservations()).isEqualTo(300);
        assertThat(result.getTotalVisitors()).isEqualTo(240);
    }

    @Test
    @DisplayName("행사·예약·방문자가 모두 0이어도 정상 반환")
    void getDashboardSummary_allZeros_returnsZeroDto() {
        given(adminDashboardMapper.selectDashboardSummary()).willReturn(makeSummary(0, 0, 0, 0, 0, 0));

        AdminDashboardSummaryDto result = adminDashboardService.getDashboardSummary();

        assertThat(result.getTotalFairs()).isZero();
        assertThat(result.getTotalVisitors()).isZero();
    }

    @Test
    @DisplayName("Mapper를 정확히 1회 호출")
    void getDashboardSummary_callsMapperOnce() {
        given(adminDashboardMapper.selectDashboardSummary()).willReturn(new AdminDashboardSummaryDto());

        adminDashboardService.getDashboardSummary();

        then(adminDashboardMapper).should(times(1)).selectDashboardSummary();
    }

    // getFairSummaryList

    @Test
    @DisplayName("행사 목록을 Mapper 결과 그대로 반환")
    void getFairSummaryList_returnsListFromMapper() {
        FairSummaryDto fair1 = makeFairSummary(1L, "펫페스타 2026", "IN_PROGRESS", 100, 80);
        FairSummaryDto fair2 = makeFairSummary(2L, "고양이 박람회", "ENDED", 200, 190);
        given(adminDashboardMapper.selectFairSummaryList()).willReturn(List.of(fair1, fair2));

        List<FairSummaryDto> result = adminDashboardService.getFairSummaryList();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getFairName()).isEqualTo("펫페스타 2026");
        assertThat(result.get(0).getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(result.get(0).getTotalReservations()).isEqualTo(100);
        assertThat(result.get(0).getTotalVisitors()).isEqualTo(80);
        assertThat(result.get(1).getFairName()).isEqualTo("고양이 박람회");
    }

    @Test
    @DisplayName("운영 중인 행사가 없으면 빈 리스트 반환")
    void getFairSummaryList_noFairs_returnsEmptyList() {
        given(adminDashboardMapper.selectFairSummaryList()).willReturn(List.of());

        List<FairSummaryDto> result = adminDashboardService.getFairSummaryList();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Mapper를 정확히 1회 호출")
    void getFairSummaryList_callsMapperOnce() {
        given(adminDashboardMapper.selectFairSummaryList()).willReturn(List.of());

        adminDashboardService.getFairSummaryList();

        then(adminDashboardMapper).should(times(1)).selectFairSummaryList();
    }

    // ── 헬퍼 메서드 ──────────────────────────────────────────────────

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
