package com.ms.petopia.api.pet.dto;

import com.ms.petopia.api.pet.domain.PetAllergyType;

/**
 * 알레르기 유형 선택지 1건(GET /api/pet-allergy-types).
 *
 * <p>{@code isActive}는 담지 않는다 - 이 API는 활성 항목만 내려주므로 항상 true다.
 */
public record PetAllergyTypeResponse(
        Long allergyTypeId,
        String code,
        String category,
        String label,
        boolean requiresText,
        Integer sortOrder
) {
    public static PetAllergyTypeResponse from(PetAllergyType type) {
        return new PetAllergyTypeResponse(
                type.getAllergyTypeId(),
                type.getCode(),
                type.getCategory(),
                type.getLabel(),
                type.isRequiresText(),
                type.getSortOrder()
        );
    }
}
