package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
public class ReservationAvailabilityFair {
    private Long fairId;
    private long reservationFee;
    private LocalDate reservationStartDate;
    private LocalDate reservationEndDate;
    private LocalDateTime publishedAt;
    private LocalDateTime canceledAt;
    /** 행사의 반려동물 동반 허용 여부(fairs.pet_allowed). */
    private boolean petAllowed;
}
