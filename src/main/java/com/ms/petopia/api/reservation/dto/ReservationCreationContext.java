package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 예약 생성에 필요한 행사와 운영일 정보를 한 번에 조회한 DB 로우.
 */
@Getter
@Setter
@ToString
public class ReservationCreationContext {

    private Long fairId;
    private String fairName;
    private String fairStatus;
    private LocalDate reservationStartDate;
    private LocalDate reservationEndDate;
    private long reservationFee;
    private LocalDateTime publishedAt;
    private LocalDateTime canceledAt;
    /** 행사의 반려동물 동반 허용 여부(fairs.pet_allowed). */
    private boolean petAllowed;
    private Long fairDateId;
    private LocalDate operationDate;
    private int capacity;
    /** 그 운영일의 입장 가능 시작 시각. 당일 사전예약 허용 여부 판정에 쓴다. */
    private LocalTime entryStartTime;
    /** 그 운영일의 입장 가능 종료 시각. 당일 사전예약 허용 여부 판정에 쓴다. */
    private LocalTime entryEndTime;
}
