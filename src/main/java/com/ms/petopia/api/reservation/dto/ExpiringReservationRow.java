package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class ExpiringReservationRow {
    private Long reservationId;
    private Long fairId; // 실시간 통계 확인용
    private LocalDate visitDate; // 만료된 좌석을 정원에 반납할 대상 운영일
    private String reservationType; // ADVANCE만 정원을 점유하므로 반납 대상을 가린다
}
