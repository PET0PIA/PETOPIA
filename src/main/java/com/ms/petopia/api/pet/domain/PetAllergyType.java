package com.ms.petopia.api.pet.domain;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 알레르기 유형 마스터 1건(pet_allergy_types).
 *
 * <p>항목이 앞으로 늘어날 수 있어 CHECK 제약이나 하드코딩이 아니라 마스터 테이블로 뒀다
 * (feedback_tags와 같은 이유). 삭제는 물리 삭제 대신 {@code isActive=false}로만 하며,
 * 이미 쌓인 선택 이력(pet_allergies·예약 스냅샷)이 가리키는 id가 계속 유효하게 남는다.
 */
@Getter
@Setter
public class PetAllergyType {

    private Long allergyTypeId;
    /** CHICKEN, POLLEN, OTHER 등. */
    private String code;
    /** FOOD / ENVIRONMENT / OTHER. */
    private String category;
    private String label;
    /** true면 직접 입력칸이 필요한 항목(기타). 프론트가 code='OTHER'를 특수 분기로 알 필요가 없게 한다. */
    private boolean requiresText;
    private Integer sortOrder;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
