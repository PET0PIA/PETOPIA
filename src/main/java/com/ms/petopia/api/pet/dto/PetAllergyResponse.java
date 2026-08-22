package com.ms.petopia.api.pet.dto;

/**
 * 반려동물에 등록된 알레르기 1건. 화면은 {@code label}을 그대로 뿌리고,
 * {@code requiresText=true}인 항목만 {@code otherText}를 덧붙여 보여준다.
 */
public record PetAllergyResponse(
        Long allergyTypeId,
        String code,
        String category,
        String label,
        boolean requiresText,
        String otherText
) {
    public static PetAllergyResponse from(PetAllergyRow row) {
        return new PetAllergyResponse(
                row.getAllergyTypeId(),
                row.getCode(),
                row.getCategory(),
                row.getLabel(),
                row.isRequiresText(),
                row.getOtherText()
        );
    }
}
