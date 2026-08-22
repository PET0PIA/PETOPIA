package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** 예약에 담긴 반려동물 스냅샷 1건(조회용). */
@Getter
@Setter
public class ReservationPetRow {

    private Long reservationPetId;
    private Long reservationId;
    private Long petId;
    private String petNameSnapshot;
    private String petSpeciesSnapshot;
    private String petBreedSnapshot;
    private LocalDate petBirthDateSnapshot;
    private Boolean petHasAllergySnapshot;
}
