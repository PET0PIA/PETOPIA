package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 예약 상세에 표시할 동반 반려동물 1마리.
 *
 * <p>예약 시점 스냅샷이라 원본(pets)이 수정·삭제돼도 이 값은 변하지 않는다.
 * {@code petId}는 원본이 아직 남아 있을 때 화면에서 상세로 이어주기 위한 참조값일 뿐이고,
 * 표시 내용은 전부 스냅샷에서 온다.
 */
public record ReservationPetResponse(
        Long reservationPetId,
        Long petId,
        String name,
        String species,
        String breed,
        LocalDate birthDate,
        Boolean hasAllergy,
        List<ReservationPetAllergyResponse> allergies
) {
    /** 예약 시점 알레르기 1건. requiresText=true인 항목만 otherText가 채워져 있다. */
    public record ReservationPetAllergyResponse(
            Long allergyTypeId,
            String code,
            String category,
            String label,
            boolean requiresText,
            String otherText
    ) {
        public static ReservationPetAllergyResponse from(ReservationPetAllergyRow row) {
            return new ReservationPetAllergyResponse(
                    row.getAllergyTypeId(),
                    row.getCode(),
                    row.getCategory(),
                    row.getLabel(),
                    row.isRequiresText(),
                    row.getOtherTextSnapshot()
            );
        }
    }

    public static ReservationPetResponse from(ReservationPetRow row, List<ReservationPetAllergyResponse> allergies) {
        return new ReservationPetResponse(
                row.getReservationPetId(),
                row.getPetId(),
                row.getPetNameSnapshot(),
                row.getPetSpeciesSnapshot(),
                row.getPetBreedSnapshot(),
                row.getPetBirthDateSnapshot(),
                row.getPetHasAllergySnapshot(),
                allergies
        );
    }
}
