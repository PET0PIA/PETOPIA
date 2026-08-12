package com.ms.petopia.api.recommendation.service;

import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.pet.service.PetService;
import com.ms.petopia.api.recommendation.domain.BoothCandidate;
import com.ms.petopia.api.recommendation.dto.BoothRecommendationItem;
import com.ms.petopia.api.recommendation.dto.BoothRecommendationRequest;
import com.ms.petopia.api.recommendation.mapper.BoothRecommendationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final BoothRecommendationMapper boothRecommendationMapper;
    private final PetService petService;
    private final ClaudeBoothRecommender claudeBoothRecommender;

    public List<BoothRecommendationItem> recommend(Long fairId, Long userId, BoothRecommendationRequest request) {

        //petId, need 둘 다 없으면 400
        boolean noPet = request.getPetId() == null;
        boolean noNeed = request.getNeed() == null || request.getNeed().isBlank();
        if (noPet && noNeed) {
            throw new CommonException(ErrorCode.RECOMMENDATION_TARGET_REQUIRED);
        }

        //petId가 있으면 로그인 필수 + 소유권 검증(PetService가 이미 다 해줌)
        PetResponse pet = null;
        if (request.getPetId() != null) {
            if (userId == null) {
                throw new CommonException(ErrorCode.UNAUTHORIZED);
            }
            pet = petService.getPet(userId, request.getPetId());
        }

        //행사 존재 확인
        if (!boothRecommendationMapper.existsFair(fairId)) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }

        //부스 조회
        List<BoothCandidate> candidates = boothRecommendationMapper.selectBoothCandidates(fairId);

        //Claude 호출
        List<ClaudeBoothRecommender.RecommendationEntry> entries =
                claudeBoothRecommender.recommend(pet, request.getNeed(), candidates);

        //Claude 결과(boothId+reason)에 부스 이름을 붙여서 최종 응답으로 변환
        return toResponse(entries, candidates);
    }

    private List<BoothRecommendationItem> toResponse(
            List<ClaudeBoothRecommender.RecommendationEntry> entries,
            List<BoothCandidate> candidates
    ) {
        Map<Long, String> nameByBoothId = candidates.stream()
                .collect(Collectors.toMap(BoothCandidate::getBoothId, BoothCandidate::getName));

        return entries.stream()
                //목록에 없는 boothId는 Claude가 지어낸 것일 수 있으니 걸러낸다
                .filter(entry -> nameByBoothId.containsKey(entry.boothId()))
                .map(entry -> new BoothRecommendationItem(
                        entry.boothId(),
                        nameByBoothId.get(entry.boothId()),
                        entry.reason()
                ))
                .toList();
    }
}
