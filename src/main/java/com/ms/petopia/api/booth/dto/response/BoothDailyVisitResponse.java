package com.ms.petopia.api.booth.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/** 부스 통계의 일자별 방문자 수 한 항목. */
@Getter
@Builder
public class BoothDailyVisitResponse {

    private LocalDate visitDate;
    private int visitorCount;

    public static BoothDailyVisitResponse from(BoothDailyVisitRow row) {

        return BoothDailyVisitResponse.builder()
                .visitDate(row.getVisitDate())
                .visitorCount(row.getVisitorCount())
                .build();

    }

}
