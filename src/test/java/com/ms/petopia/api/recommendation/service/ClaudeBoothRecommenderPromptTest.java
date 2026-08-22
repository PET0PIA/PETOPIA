package com.ms.petopia.api.recommendation.service;

import com.ms.petopia.api.pet.dto.PetAllergyResponse;
import com.ms.petopia.api.pet.dto.PetResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClaudeBoothRecommenderPromptTest {

    @Test
    void 선택값이_없는_반려동물은_알레르기_정보를_표시하지_않는다() throws Exception {
        PetResponse pet = pet(null, List.of(), null, null);

        String prompt = buildUserPrompt(List.of(pet));

        assertThat(prompt).contains("- 콩이: 강아지");
        assertThat(prompt).doesNotContain("null", "살", "수컷", "암컷", "알레르기");
    }

    @Test
    void 알레르기_있음은_라벨을_표시하고_OTHER는_직접입력값을_표시한다() throws Exception {
        PetResponse pet = pet(
                true,
                List.of(
                        new PetAllergyResponse(1L, "CHICKEN", "FOOD", "닭고기", false, null),
                        new PetAllergyResponse(2L, "OTHER", "OTHER", "기타", true, "옥수수 전분")
                ),
                "Maltese",
                "MALE"
        );

        String prompt = buildUserPrompt(List.of(pet));

        assertThat(prompt).contains("알레르기: 닭고기·옥수수 전분");
        assertThat(prompt).doesNotContain("기타");
    }

    @Test
    void 알레르기_없음은_명시한다() throws Exception {
        String prompt = buildUserPrompt(List.of(pet(false, List.of(), null, null)));

        assertThat(prompt).contains("알레르기 없음");
    }

    private String buildUserPrompt(List<PetResponse> pets) throws Exception {
        ClaudeBoothRecommender recommender = new ClaudeBoothRecommender("");
        Method method = ClaudeBoothRecommender.class.getDeclaredMethod(
                "buildUserPrompt", List.class, String.class, List.class
        );
        method.setAccessible(true);
        return (String) method.invoke(recommender, pets, null, List.of());
    }

    private PetResponse pet(Boolean hasAllergy, List<PetAllergyResponse> allergies,
                            String breed, String gender) {
        return new PetResponse(
                1L, "콩이", "강아지", breed, null, gender, false, null, null,
                hasAllergy, allergies
        );
    }
}
