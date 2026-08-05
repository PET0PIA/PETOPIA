package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import com.ms.petopia.api.statistics.mapper.ReservationDashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;


@Service
@RequiredArgsConstructor
public class ReservationDashboardService {
    private final ReservationDashboardMapper dashboardMapper;

    @Transactional(readOnly = true)
    public List<ReservationDateSummaryDto> getDateSummary(Long fairId, LocalDate date){
        return dashboardMapper.selectDateSummaryList(fairId, date);
    }
}
