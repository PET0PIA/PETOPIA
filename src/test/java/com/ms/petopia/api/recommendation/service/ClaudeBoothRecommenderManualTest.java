package com.ms.petopia.api.recommendation.service;

import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.recommendation.domain.BoothCandidate;
import com.ms.petopia.api.recommendation.domain.BoothItemCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/*
 * 실제 Claude API를 호출하는 수동 확인용 테스트(DB/Spring 컨텍스트 없이 이 클래스만 단독으로 테스트).
 * ANTHROPIC_API_KEY 환경변수가 있을 때만 실행되고, 없으면(CI 등) 자동으로 스킵된다.
 * 실행할 때마다 실제 과금이 발생하니 평소 테스트 스위트 돌릴 때 습관적으로 실행되지 않게 분리해뒀다.
 */
class ClaudeBoothRecommenderManualTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
    void 실제_Claude_API를_호출해서_추천을_받아온다() {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        ClaudeBoothRecommender recommender = new ClaudeBoothRecommender(apiKey);

        BoothItemCandidate item = new BoothItemCandidate();
        item.setName("관절 영양제");
        item.setType("PRODUCT");
        item.setNote("글루코사민 함유, 노령견 추천");

        BoothCandidate boothForDog = new BoothCandidate();
        boothForDog.setBoothId(1L);
        boothForDog.setName("A부스");
        boothForDog.setCategory("사료/간식");
        boothForDog.setTargetAnimal("DOG");
        boothForDog.setIntro("반려동물 관절 건강 전문 브랜드");
        boothForDog.setItems(List.of(item));

        BoothCandidate boothForCat = new BoothCandidate();
        boothForCat.setBoothId(2L);
        boothForCat.setName("B부스");
        boothForCat.setCategory("굿즈");
        boothForCat.setTargetAnimal("CAT");
        boothForCat.setIntro("고양이 장난감 전문점");
        boothForCat.setItems(List.of());

        PetResponse pet = new PetResponse(
                1L, "말티", "강아지", "말티즈",
                LocalDate.of(2018, 3, 1), "MALE", true, null, null
        );

        List<ClaudeBoothRecommender.RecommendationEntry> result =
                recommender.recommend(pet, "관절에 좋은 거 찾아요", List.of(boothForDog, boothForCat));

        result.forEach(entry -> System.out.println("boothId=" + entry.boothId() + ", reason=" + entry.reason()));

        //노령견 관절 영양제를 찾는 요청이니, 고양이용 B부스보다 강아지 관절 영양제인 A부스가 추천되길 기대
        assertThat(result).isNotEmpty();
        assertThat(result).anyMatch(entry -> entry.boothId().equals(1L));
    }
}
