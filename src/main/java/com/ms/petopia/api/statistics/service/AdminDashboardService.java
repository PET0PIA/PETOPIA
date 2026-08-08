package com.ms.petopia.api.statistics.service;

import com.ms.petopia.api.statistics.dto.AdminDashboardSummaryDto;
import com.ms.petopia.api.statistics.dto.FairSummaryDto;
import com.ms.petopia.api.statistics.mapper.AdminDashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private final AdminDashboardMapper adminDashboardMapper;

    @Transactional(readOnly = true)
    public AdminDashboardSummaryDto getDashboardSummary(){
        return adminDashboardMapper.selectDashboardSummary();
    }

    @Transactional(readOnly = true)
    public List<FairSummaryDto> getFairSummaryList(){
        return adminDashboardMapper.selectFairSummaryList();
    }
}
