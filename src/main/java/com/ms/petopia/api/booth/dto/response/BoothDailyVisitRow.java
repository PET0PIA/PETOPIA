package com.ms.petopia.api.booth.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 부스 하나의 일자별 고유 방문자 수 한 행. {@code BoothStatsMapper.selectDailyVisitCounts} 전용 결과. */
@Getter
@Setter
public class BoothDailyVisitRow {
    private LocalDate visitDate;
    private int visitorCount;
}
