package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import com.ms.petopia.api.statistics.mapper.ReservationDashboardMapper;
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
class ReservationDashboardServiceTest {

    private static final Long FAIR_ID = 1L;
    private static final LocalDate TARGET_DATE = LocalDate.of(2026, 8, 1);

    @Mock
    private ReservationDashboardMapper dashboardMapper;

    @InjectMocks
    private ReservationDashboardService dashboardService;

    @Test
    @DisplayName("date 없으면 Mapper에 null 전달해 전체 날짜 조회")
    void getDateSummary_noDate_passesNullToMapper() {
        // given
        List<ReservationDateSummaryDto> expected = List.of(
                new ReservationDateSummaryDto(),
                new ReservationDateSummaryDto()
        );
        given(dashboardMapper.selectDateSummaryList(FAIR_ID, null)).willReturn(expected);

        // when
        List<ReservationDateSummaryDto> result = dashboardService.getDateSummary(FAIR_ID, null);

        // then
        assertThat(result).hasSize(2);
        then(dashboardMapper).should(times(1)).selectDateSummaryList(FAIR_ID, null);
    }

    @Test
    @DisplayName("date 있으면 해당 날짜를 Mapper에 그대로 전달")
    void getDateSummary_withDate_passesDateToMapper() {
        // given
        List<ReservationDateSummaryDto> expected = List.of(new ReservationDateSummaryDto());
        given(dashboardMapper.selectDateSummaryList(FAIR_ID, TARGET_DATE)).willReturn(expected);

        // when
        List<ReservationDateSummaryDto> result = dashboardService.getDateSummary(FAIR_ID, TARGET_DATE);

        // then
        assertThat(result).hasSize(1);
        then(dashboardMapper).should(times(1)).selectDateSummaryList(FAIR_ID, TARGET_DATE);
    }

    @Test
    @DisplayName("존재하지 않는 fairId면 빈 리스트 반환")
    void getDateSummary_notExistFairId_returnsEmptyList() {
        // given
        given(dashboardMapper.selectDateSummaryList(999L, null)).willReturn(List.of());

        // when
        List<ReservationDateSummaryDto> result = dashboardService.getDateSummary(999L, null);

        // then
        assertThat(result).isEmpty();
    }
}
