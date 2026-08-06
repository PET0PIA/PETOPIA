package com.ms.petopia.api.fair.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 운영일 조회/생성/수정 응답.
 *
 * <p>{@code reservedCount}/{@code onsiteSalesConfigured}는 fair_dates 자체 컬럼이 아니라
 * 예약 도메인 테이블을 참고용으로 집계한 값이다. 관리자 화면이 정원 축소·삭제 전에
 * 경고를 보여주는 용도로만 쓰고, 서버는 이 값으로 정원 축소·삭제 자체를 막지 않는다
 * (경고 후 계속 진행할지는 관리자가 판단).
 */
public record FairDateResponse(
        Long fairDateId,
        Long fairId,
        LocalDate operationDate,
        Integer capacity,
        LocalTime entryStartTime,
        LocalTime entryEndTime,
        int reservedCount,
        boolean onsiteSalesConfigured,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
