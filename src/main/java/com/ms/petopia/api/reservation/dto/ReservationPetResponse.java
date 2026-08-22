package com.ms.petopia.api.reservation.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 예약 상세에 표시할 동반 반려동물 1마리.
 *
 * <p>예약 시점 스냅샷이라 원본(pets)이 수정·삭제돼도 이 값은 변하지 않는다.
 * {@code petId}는 원본이 아직 남아 있을 때 화면에서 상세로 이어주기 위한 참조값일 뿐이고,
 * 반려동물 자체의 표시 내용은 전부 스냅샷에서 온다.
 *
 * <p><b>예외는 알레르기 라벨이다</b> - {@link ReservationPetAllergyResponse}의 {@code code}·
 * {@code category}·{@code label}·{@code requiresText}는 스냅샷이 아니라 마스터
 * (pet_allergy_types)에서 조회 시점에 읽어온다. 마스터의 라벨을 바꾸지 않는다는 전제로
 * 성립하는 구조다 - 자세한 이유와 전제가 깨질 때의 대응은 ReservationPetMapper.xml의
 * {@code selectAllergiesByReservationPetIds} 주석과 계획서 §9에 있다.
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
    /**
     * 예약 시점 알레르기 1건. requiresText=true인 항목만 otherText가 채워져 있다.
     *
     * <p>otherText만 스냅샷이다. 나머지 네 필드는 마스터에서 읽어오므로, 마스터를 수정하면
     * 과거 예약의 표시가 함께 바뀐다(바꾸지 않는 것이 규칙이다 - 위 클래스 주석 참고).
     */
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
