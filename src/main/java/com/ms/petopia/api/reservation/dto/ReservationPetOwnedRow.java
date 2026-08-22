package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 예약에 담을 후보로 조회한 "예약자 본인 소유" 반려동물 1건(pets 조회 결과).
 * 이 값들을 그대로 예약 스냅샷으로 복사한다.
 */
@Getter
@Setter
public class ReservationPetOwnedRow {

    private Long petId;
    private String name;
    private String species;
    private String breed;
    private LocalDate birthDate;
    private Boolean hasAllergy;
}
