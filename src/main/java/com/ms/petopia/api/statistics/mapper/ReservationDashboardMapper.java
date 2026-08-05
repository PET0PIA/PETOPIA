package com.ms.petopia.api.statistics.mapper;

import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ReservationDashboardMapper {
    List<ReservationDateSummaryDto> selectDateSummaryList(@Param("fairId") Long fairId);
}
