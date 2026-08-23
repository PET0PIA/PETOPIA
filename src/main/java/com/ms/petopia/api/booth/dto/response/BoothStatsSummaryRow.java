package com.ms.petopia.api.booth.dto.response;

import lombok.Getter;
import lombok.Setter;

/** 부스 하나의 방문·리뷰·찜 전환 통계 요약 한 행. {@code BoothStatsMapper.selectSummary} 전용 결과. */
@Getter
@Setter
public class BoothStatsSummaryRow {
    private int uniqueVisitorCount;
    private long totalScanCount;
    private int revisitCount;
    private int reviewedVisitorCount;
    private int favoritedVisitorCount;
}
