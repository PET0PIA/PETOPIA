package com.ms.petopia.api.statistics.mapper;

import com.ms.petopia.api.statistics.dto.AdminDashboardSummaryDto;
import com.ms.petopia.api.statistics.dto.FairSummaryDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface AdminDashboardMapper {
    AdminDashboardSummaryDto selectDashboardSummary();
    List<FairSummaryDto> selectFairSummaryList();
}
