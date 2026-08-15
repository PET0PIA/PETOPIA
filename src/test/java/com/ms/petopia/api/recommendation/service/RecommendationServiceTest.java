package com.ms.petopia.api.recommendation.service;

import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.pet.service.PetService;
import com.ms.petopia.api.recommendation.domain.BoothCandidate;
import com.ms.petopia.api.recommendation.domain.BoothSlotLocation;
import com.ms.petopia.api.recommendation.dto.BoothRecommendationItem;
import com.ms.petopia.api.recommendation.dto.BoothRecommendationRequest;
import com.ms.petopia.api.recommendation.mapper.BoothRecommendationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    private static final Long FAIR_ID = 1L;
    private static final Long USER_ID = 1L;
    private static final Long PET_ID = 1L;

    @Mock
    private BoothRecommendationMapper boothRecommendationMapper;
    @Mock
    private PetService petService;
    @Mock
    private ClaudeBoothRecommender claudeBoothRecommender;
    @InjectMocks
    private RecommendationService recommendationService;

    @Test
    void petId와_need가_둘다_없으면_RECOMMENDATION_TARGET_REQUIRED를_던진다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();

        assertThatThrownBy(() -> recommendationService.recommend(FAIR_ID, USER_ID, request))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.RECOMMENDATION_TARGET_REQUIRED);

        verify(boothRecommendationMapper, never()).existsFair(anyLong());
    }

    @Test
    void petId가_있는데_비로그인이면_UNAUTHORIZED를_던진다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setPetId(PET_ID);

        assertThatThrownBy(() -> recommendationService.recommend(FAIR_ID, null, request))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);

        verify(petService, never()).getPet(any(), any());
    }

    @Test
    void 존재하지_않는_행사면_FAIR_NOT_FOUND를_던진다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setNeed("관절에 좋은 거 찾아요");
        given(boothRecommendationMapper.existsFair(FAIR_ID)).willReturn(false);

        assertThatThrownBy(() -> recommendationService.recommend(FAIR_ID, null, request))
                .isInstanceOf(CommonException.class)
                .extracting(ex -> ((CommonException) ex).getErrorCode())
                .isEqualTo(ErrorCode.FAIR_NOT_FOUND);

        verify(boothRecommendationMapper, never()).selectBoothCandidates(anyLong());
    }

    @Test
    void need만_있으면_비로그인도_추천을_받을_수_있다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setNeed("관절에 좋은 거 찾아요");

        given(boothRecommendationMapper.existsFair(FAIR_ID)).willReturn(true);
        BoothCandidate candidate = boothCandidate(1L, "A부스");
        given(boothRecommendationMapper.selectBoothCandidates(FAIR_ID)).willReturn(List.of(candidate));
        given(claudeBoothRecommender.recommend(null, "관절에 좋은 거 찾아요", List.of(candidate)))
                .willReturn(List.of(new ClaudeBoothRecommender.RecommendationEntry(1L, "관절 영양제 판매")));

        List<BoothRecommendationItem> result = recommendationService.recommend(FAIR_ID, null, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).boothId()).isEqualTo(1L);
        assertThat(result.get(0).boothName()).isEqualTo("A부스");
        assertThat(result.get(0).reason()).isEqualTo("관절 영양제 판매");
        verify(petService, never()).getPet(any(), any());
    }

    @Test
    void petId가_있으면_소유한_반려동물_프로필을_같이_넘긴다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setPetId(PET_ID);

        PetResponse pet = new PetResponse(PET_ID, "말티", "강아지", "말티즈",
                LocalDate.of(2018, 3, 1), "MALE", true, null, null);
        given(petService.getPet(USER_ID, PET_ID)).willReturn(pet);
        given(boothRecommendationMapper.existsFair(FAIR_ID)).willReturn(true);
        BoothCandidate candidate = boothCandidate(1L, "A부스");
        given(boothRecommendationMapper.selectBoothCandidates(FAIR_ID)).willReturn(List.of(candidate));
        given(claudeBoothRecommender.recommend(pet, null, List.of(candidate)))
                .willReturn(List.of(new ClaudeBoothRecommender.RecommendationEntry(1L, "말티즈 맞춤 부스")));

        List<BoothRecommendationItem> result = recommendationService.recommend(FAIR_ID, USER_ID, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).boothName()).isEqualTo("A부스");
    }

    @Test
    void Claude가_후보목록에_없는_boothId를_추천하면_걸러낸다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setNeed("장난감 찾아요");

        given(boothRecommendationMapper.existsFair(FAIR_ID)).willReturn(true);
        BoothCandidate candidate = boothCandidate(1L, "A부스");
        given(boothRecommendationMapper.selectBoothCandidates(FAIR_ID)).willReturn(List.of(candidate));
        //Claude가 후보 목록에 없는 999번 부스를 지어낸 상황을 흉내
        given(claudeBoothRecommender.recommend(null, "장난감 찾아요", List.of(candidate)))
                .willReturn(List.of(new ClaudeBoothRecommender.RecommendationEntry(999L, "지어낸 부스")));

        List<BoothRecommendationItem> result = recommendationService.recommend(FAIR_ID, null, request);

        assertThat(result).isEmpty();
    }

    @Test
    void 후보_부스가_없으면_Claude를_호출하지_않고_빈_목록을_반환한다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setNeed("장난감 찾아요");

        given(boothRecommendationMapper.existsFair(FAIR_ID)).willReturn(true);
        given(boothRecommendationMapper.selectBoothCandidates(FAIR_ID)).willReturn(List.of());

        List<BoothRecommendationItem> result = recommendationService.recommend(FAIR_ID, null, request);

        assertThat(result).isEmpty();
        verify(claudeBoothRecommender, never()).recommend(any(), any(), any());
    }

    @Test
    void Claude가_6개_이상_추천해도_5개로_제한한다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setNeed("장난감 찾아요");

        given(boothRecommendationMapper.existsFair(FAIR_ID)).willReturn(true);
        List<BoothCandidate> candidates = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(i -> boothCandidate((long) i, "부스" + i))
                .toList();
        given(boothRecommendationMapper.selectBoothCandidates(FAIR_ID)).willReturn(candidates);
        List<ClaudeBoothRecommender.RecommendationEntry> entries = java.util.stream.IntStream.rangeClosed(1, 6)
                .mapToObj(i -> new ClaudeBoothRecommender.RecommendationEntry((long) i, "이유" + i))
                .toList();
        given(claudeBoothRecommender.recommend(null, "장난감 찾아요", candidates)).willReturn(entries);

        List<BoothRecommendationItem> result = recommendationService.recommend(FAIR_ID, null, request);

        assertThat(result).hasSize(5);
    }

    @Test
    void Claude가_같은_boothId를_중복으로_추천하면_한_번만_남긴다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setNeed("장난감 찾아요");

        given(boothRecommendationMapper.existsFair(FAIR_ID)).willReturn(true);
        BoothCandidate candidate = boothCandidate(1L, "A부스");
        given(boothRecommendationMapper.selectBoothCandidates(FAIR_ID)).willReturn(List.of(candidate));
        //Claude가 같은 부스를 두 번 추천한 상황을 흉내
        given(claudeBoothRecommender.recommend(null, "장난감 찾아요", List.of(candidate)))
                .willReturn(List.of(
                        new ClaudeBoothRecommender.RecommendationEntry(1L, "첫 번째 이유"),
                        new ClaudeBoothRecommender.RecommendationEntry(1L, "두 번째 이유")
                ));

        List<BoothRecommendationItem> result = recommendationService.recommend(FAIR_ID, null, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).reason()).isEqualTo("첫 번째 이유");
    }

    @Test
    void 추천된_부스에_홀_슬롯_위치_정보를_합친다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setNeed("장난감 찾아요");

        given(boothRecommendationMapper.existsFair(FAIR_ID)).willReturn(true);
        BoothCandidate candidate = boothCandidate(1L, "A부스");
        given(boothRecommendationMapper.selectBoothCandidates(FAIR_ID)).willReturn(List.of(candidate));
        given(claudeBoothRecommender.recommend(null, "장난감 찾아요", List.of(candidate)))
                .willReturn(List.of(new ClaudeBoothRecommender.RecommendationEntry(1L, "장난감 많아요")));
        given(boothRecommendationMapper.selectBoothLocations(List.of(1L)))
                .willReturn(List.of(boothSlotLocation(1L, "A홀", "A-01")));

        List<BoothRecommendationItem> result = recommendationService.recommend(FAIR_ID, null, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).hallName()).isEqualTo("A홀");
        assertThat(result.get(0).slotNumber()).isEqualTo("A-01");
    }

    @Test
    void 한_부스가_슬롯을_여러_개_차지하면_첫_번째_슬롯만_사용한다() {
        BoothRecommendationRequest request = new BoothRecommendationRequest();
        request.setNeed("장난감 찾아요");

        given(boothRecommendationMapper.existsFair(FAIR_ID)).willReturn(true);
        BoothCandidate candidate = boothCandidate(1L, "A부스");
        given(boothRecommendationMapper.selectBoothCandidates(FAIR_ID)).willReturn(List.of(candidate));
        given(claudeBoothRecommender.recommend(null, "장난감 찾아요", List.of(candidate)))
                .willReturn(List.of(new ClaudeBoothRecommender.RecommendationEntry(1L, "장난감 많아요")));
        //ORDER BY b.booth_id, bs.booth_slot_id로 정렬돼 오므로, 매퍼가 반환하는 리스트 순서상 첫 번째가 booth_slot_id가 가장 작은 슬롯
        given(boothRecommendationMapper.selectBoothLocations(List.of(1L)))
                .willReturn(List.of(
                        boothSlotLocation(1L, "A홀", "A-01"),
                        boothSlotLocation(1L, "A홀", "A-02")
                ));

        List<BoothRecommendationItem> result = recommendationService.recommend(FAIR_ID, null, request);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).slotNumber()).isEqualTo("A-01");
    }

    private BoothSlotLocation boothSlotLocation(Long boothId, String hallName, String slotNumber) {
        BoothSlotLocation location = new BoothSlotLocation();
        location.setBoothId(boothId);
        location.setHallName(hallName);
        location.setSlotNumber(slotNumber);
        return location;
    }

    private BoothCandidate boothCandidate(Long boothId, String name) {
        BoothCandidate candidate = new BoothCandidate();
        candidate.setBoothId(boothId);
        candidate.setName(name);
        candidate.setCategory("사료/간식");
        candidate.setTargetAnimal("DOG");
        candidate.setIntro("소개");
        candidate.setItems(List.of());
        return candidate;
    }
}
