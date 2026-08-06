package com.ms.petopia.api.statistics.mapper;

import com.ms.petopia.api.statistics.dto.BoothVisitStatDto;
import com.ms.petopia.api.statistics.dto.HourlyEntryTrendDto;
import com.ms.petopia.api.statistics.dto.QrIssuanceSummaryDto;
import com.ms.petopia.api.statistics.dto.ReservationDateSummaryDto;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReservationDashboardMapper {

    // null이면 전체 날짜, 값이 있으면 해당 날짜만 조회
    List<ReservationDateSummaryDto> selectDateSummaryList(
            @Param("fairId") Long fairId,
            @Param("date") LocalDate date
    );

    List<QrIssuanceSummaryDto> selectQrIssuanceSummary(
            @Param("fairId") Long fairId
    );

    // date 필수: 특정 운영일의 시간대별 입장 건수
    List<HourlyEntryTrendDto> selectHourlyEntryTrend(
            @Param("fairId") Long fairId,
            @Param("date") LocalDate date
    );

    List<BoothVisitStatDto> selectBoothVisitStats(
            @Param("fairId") Long fairId
    );
}
