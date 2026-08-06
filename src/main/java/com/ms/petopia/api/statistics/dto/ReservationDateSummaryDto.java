package com.ms.petopia.api.statistics.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * EVENT_ADMIN 대시보드 상단: 운영일마다 한 행씩 표시되는 집계 요약.
 *
 * fair_dates 를 LEFT JOIN 기준으로 삼으므로 예약이 0건인 날짜도 반드시 포함된다.
 */
@Getter
@Setter
@ToString
public class ReservationDateSummaryDto {
    private Long fairDateId; //식별자로 사용
    private LocalDate operationDate;
    private int capacity; // 해당 날짜 총 정원
    private LocalTime entryStartTime; // 입장시작
    private LocalTime entryEndTime; // 입장 종료

    // 상태별 건수
    private int totalCount; // 전체
    private int confirmedCount;
    private int checkedInCount; // 입장 완료
    private int pendingCount; // 결제 대기
    private int canceledCount;
    private int expiredCount;

    private int remainingCapacity; // 잔여 정원

}
