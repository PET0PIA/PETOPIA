package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 운영일 등록 요청. fairId는 요청 본문이 아니라 경로에서 받는다.
 */
public record CreateFairDateRequest(
        LocalDate operationDate,
        Integer capacity,
        LocalTime entryStartTime,
        LocalTime entryEndTime
) {
}
