package com.ms.petopia.api.reservation.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** reservation_pet_allergies에 INSERT할 알레르기 스냅샷 1건(정규화 경로). */
@Getter
@AllArgsConstructor
public class ReservationPetAllergyInsertRow {

    private final Long reservationPetId;
    private final Long allergyTypeId;
    private final String otherTextSnapshot;
}
