package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

/** 예약에 담을 반려동물의 현재 알레르기 1건(pet_allergies 조회 결과). 스냅샷의 재료다. */
@Getter
@Setter
public class ReservationPetOwnedAllergyRow {

    private Long petId;
    private Long allergyTypeId;
    private String label;
    private boolean requiresText;
    private String otherText;
}
