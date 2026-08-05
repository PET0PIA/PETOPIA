package com.ms.petopia.api.statistics.mapper;

import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReservationDashboardMapper {
    List<ReservationDateSummaryDto> selectDateSummaryList(
            @Param("fairId") Long fairId,
            @Param("date")LocalDate date // null이면 전체 날짜, 값이 있으면 해당 날짜
    );
}
