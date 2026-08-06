package com.ms.petopia.api.fair.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * fair_dates 테이블 매핑 객체. 행사(fairs) 하위, 운영일별 정원·입장 가능 시간을 보유한다.
 *
 * <p>예약 도메인이 이 테이블을 조인해서 예약 가능 여부·정원을 판단한다
 * ({@code ReservationMapper.selectAvailabilityDates} 등). 컬럼을 바꿀 때 그쪽 쿼리도 함께 확인해야 한다.
 *
 * <p>record가 아닌 이유는 {@link Fair}와 동일 - MyBatis 세터 기반 매핑.
 */
@Getter
@Setter
@ToString
public class FairDate {

    private Long fairDateId;

    /** fairs.fair_id */
    private Long fairId;

    /** 운영 날짜. (fair_id, operation_date)가 유니크 키다 */
    private LocalDate operationDate;

    /** 해당 날짜 예약 정원 */
    private Integer capacity;

    private LocalTime entryStartTime;
    private LocalTime entryEndTime;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
