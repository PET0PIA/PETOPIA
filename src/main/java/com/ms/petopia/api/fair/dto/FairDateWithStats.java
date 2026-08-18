package com.ms.petopia.api.fair.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * fair_dates 조회 시 예약 도메인 집계(reservedCount/onsiteSalesConfigured)까지 한 번에
 * 담아오는 매퍼 전용 행(row). {@code FairDateService}가 이 값을 {@link FairDateResponse}로 변환한다.
 *
 * <p>record가 아닌 이유는 {@link FairDate}와 동일 - MyBatis 세터 기반 매핑.
 */
@Getter
@Setter
public class FairDateWithStats {
    private Long fairDateId;
    private Long fairId;
    private LocalDate operationDate;
    private Integer capacity;
    private LocalTime entryStartTime;
    private LocalTime entryEndTime;

    /** 유효 상태(PENDING_PAYMENT/CONFIRMED/CHECKED_IN)의 사전예약(ADVANCE) 건수 */
    private int reservedCount;

    /** 이 운영일에 현장예매 정책(onsite_sales_policies)이 설정되어 있는지 */
    private boolean onsiteSalesConfigured;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
