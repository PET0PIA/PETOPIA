package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * (예약, 부스) 조합의 기존 방문 기록. 재방문 판별과 응답용으로 잠금 조회한다.
 */
@Getter
@Setter
public class BoothVisitRecord {
    private Long boothVisitId;
    private int visitCount;
    private LocalDateTime firstVisitedAt;
}
