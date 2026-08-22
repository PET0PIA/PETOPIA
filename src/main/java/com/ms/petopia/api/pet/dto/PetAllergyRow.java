package com.ms.petopia.api.pet.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * pet_id별 알레르기 선택 1건(마스터 라벨까지 조인한 형태).
 *
 * <p>반려동물 목록 조회에서 N+1을 피하기 위해 여러 pet_id의 알레르기를 한 번에 가져와
 * 서비스에서 pet_id로 그룹핑한다(리뷰 도메인의 ReviewTagLabelRow와 같은 방식).
 */
@Getter
@Setter
public class PetAllergyRow {

    private Long petId;
    private Long allergyTypeId;
    private String code;
    private String category;
    private String label;
    private boolean requiresText;
    private String otherText;
}
