package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ExpiringReservationRow {
    private Long reservationId;
    private Long fairId; // 실시간 통계 확인용
}
