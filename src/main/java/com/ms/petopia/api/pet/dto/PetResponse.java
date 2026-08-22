package com.ms.petopia.api.pet.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record PetResponse(
        Long petId,
        String name,
        String species,
        String breed,
        LocalDate birthDate,
        String gender,
        Boolean isNeutered,
        String imageUrl,
        LocalDateTime createdAt,
        /** 알레르기 여부 3값. null=미입력 / false=없음 / true=있음. */
        Boolean hasAllergy,
        List<PetAllergyResponse> allergies
) {
    /**
     * 알레르기 필드가 없던 시절의 호출부 호환용. 미입력(null) + 빈 목록으로 채운다.
     *
     * <p>AI 부스 추천 도메인 테스트가 9개 인자 생성자를 직접 호출하고 있어, 이 생성자가
     * 없으면 필드를 추가한 순간 그 테스트들이 컴파일되지 않는다. 알레르기 작업이 AI
     * 도메인 파일을 건드리지 않고 지나가기 위한 자리다.
     * (RecommendationServiceTest, ClaudeBoothRecommenderManualTest)
     */
    public PetResponse(Long petId, String name, String species, String breed,
                       LocalDate birthDate, String gender, Boolean isNeutered,
                       String imageUrl, LocalDateTime createdAt) {
        this(petId, name, species, breed, birthDate, gender, isNeutered,
                imageUrl, createdAt, null, List.of());
    }
}
