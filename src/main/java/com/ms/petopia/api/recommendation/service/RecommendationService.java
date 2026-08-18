package com.ms.petopia.api.recommendation.service;

import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.pet.service.PetService;
import com.ms.petopia.api.recommendation.domain.BoothCandidate;
import com.ms.petopia.api.recommendation.domain.BoothSlotLocation;
import com.ms.petopia.api.recommendation.dto.BoothRecommendationItem;
import com.ms.petopia.api.recommendation.dto.BoothRecommendationRequest;
import com.ms.petopia.api.recommendation.dto.BoothRouteItem;
import com.ms.petopia.api.recommendation.dto.HallRoute;
import com.ms.petopia.api.recommendation.mapper.BoothRecommendationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    //홀 하나당 동선 계산에 포함할 부스 최대 개수 (matched 우선으로 채우고 넘치면 자름)
    private static final int MAX_HALL_GROUP_SIZE = 6;

    private final BoothRecommendationMapper boothRecommendationMapper;
    private final PetService petService;
    private final ClaudeBoothRecommender claudeBoothRecommender;

    public List<BoothRecommendationItem> recommend(Long fairId, Long userId, BoothRecommendationRequest request) {

        //petId/need 검증 + petId 있으면 로그인·소유권 검증까지 (recommendRoute()와 공유하는 로직)
        PetResponse pet = resolveTarget(userId, request);

        //행사 존재 확인
        if (!boothRecommendationMapper.existsFair(fairId)) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }

        //부스 조회
        List<BoothCandidate> candidates = boothRecommendationMapper.selectBoothCandidates(fairId);
        if (candidates.isEmpty()) {
            return List.of();
        }

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
        Set<Long> seenBoothIds = new HashSet<>();

        //최종 추천 부스(최대 5개)를 먼저 확정 - 위치 조회를 하려면 boothId 목록이 미리 정해져 있어야 함
        List<ClaudeBoothRecommender.RecommendationEntry> finalEntries = entries.stream()
                //목록에 없는 boothId는 Claude가 지어낸 것일 수 있으니 걸러낸다
                .filter(entry -> nameByBoothId.containsKey(entry.boothId()))
                //같은 boothId를 Claude가 중복으로 반환했을 수 있으니 첫 항목만 남긴다
                .filter(entry -> seenBoothIds.add(entry.boothId()))
                .limit(5)
                .toList();

        Map<Long, BoothSlotLocation> firstSlotByBoothId = loadFirstSlotByBoothId(finalEntries);

        return finalEntries.stream()
                .map(entry -> {
                    BoothSlotLocation location = firstSlotByBoothId.get(entry.boothId());
                    return new BoothRecommendationItem(
                            entry.boothId(),
                            nameByBoothId.get(entry.boothId()),
                            entry.reason(),
                            location != null ? location.getHallName() : null,
                            location != null ? location.getSlotNumber() : null
                    );
                })
                .toList();
    }

    //boothId별 첫 번째 슬롯 위치를 조회한다.
    private Map<Long, BoothSlotLocation> loadFirstSlotByBoothId(
            List<ClaudeBoothRecommender.RecommendationEntry> finalEntries
    ) {
        if (finalEntries.isEmpty()) {
            //IN () 은 SQL 문법 오류라 빈 리스트면 아예 매퍼를 호출하지 않는다
            return Map.of(
            );
        }

        List<Long> boothIds = finalEntries.stream()
                .map(ClaudeBoothRecommender.RecommendationEntry::boothId)
                .toList();

        return boothRecommendationMapper.selectBoothLocations(boothIds).stream()
                .collect(Collectors.toMap(
                        BoothSlotLocation::getBoothId,
                        location -> location,
                        (first, second) -> first
                ));
    }

    //petId/need 검증 + petId가 있으면 로그인·소유권 검증까지 하고 PetResponse를 돌려준다 (없으면 null)
    private PetResponse resolveTarget(Long userId, BoothRecommendationRequest request) {
        boolean noPet = request.getPetId() == null;
        boolean noNeed = request.getNeed() == null || request.getNeed().isBlank();
        if (noPet && noNeed) {
            throw new CommonException(ErrorCode.RECOMMENDATION_TARGET_REQUIRED);
        }
        if (request.getPetId() == null) {
            return null;
        }
        if (userId == null) {
            throw new CommonException(ErrorCode.UNAUTHORIZED);
        }
        return petService.getPet(userId, request.getPetId());
    }

    //동선 추천. 홀별로 그룹핑해서 각 홀 안에서 최단 동선 순서를 계산한다.
    public List<HallRoute> recommendRoute(Long fairId, Long userId, BoothRecommendationRequest request) {

        PetResponse pet = resolveTarget(userId, request);

        //행사 존재 확인
        if (!boothRecommendationMapper.existsFair(fairId)) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }

        //부스 조회
        List<BoothCandidate> candidates = boothRecommendationMapper.selectBoothCandidates(fairId);
        if (candidates.isEmpty()) {
            return List.of();
        }

        //Claude 호출 (matched 최대 8 + extra 최대 3)
        List<ClaudeBoothRecommender.RouteRecommendationEntry> entries =
                claudeBoothRecommender.recommendForRoute(pet, request.getNeed(), candidates);

        return toHallRoutes(entries, candidates);
    }

    private List<HallRoute> toHallRoutes(
            List<ClaudeBoothRecommender.RouteRecommendationEntry> entries,
            List<BoothCandidate> candidates
    ) {
        Map<Long, String> nameByBoothId = candidates.stream()
                .collect(Collectors.toMap(BoothCandidate::getBoothId, BoothCandidate::getName));
        Set<Long> seenBoothIds = new HashSet<>();

        //목록에 없는/중복된 boothId 제거. matched를 우선으로 정렬
        List<ClaudeBoothRecommender.RouteRecommendationEntry> orderedEntries = entries.stream()
                .filter(entry -> nameByBoothId.containsKey(entry.boothId()))
                .filter(entry -> seenBoothIds.add(entry.boothId()))
                .sorted(Comparator.comparing(ClaudeBoothRecommender.RouteRecommendationEntry::matched).reversed())
                .toList();

        if (orderedEntries.isEmpty()) {
            return List.of();
        }

        List<Long> boothIds = orderedEntries.stream()
                .map(ClaudeBoothRecommender.RouteRecommendationEntry::boothId)
                .toList();
        Map<Long, BoothSlotLocation> firstSlotByBoothId = boothRecommendationMapper.selectBoothLocations(boothIds).stream()
                .collect(Collectors.toMap(
                        BoothSlotLocation::getBoothId,
                        location -> location,
                        (first, second) -> first
                ));

        //슬롯 위치가 없는 부스 제외
        List<ClaudeBoothRecommender.RouteRecommendationEntry> locatedEntries = orderedEntries.stream()
                .filter(entry -> firstSlotByBoothId.containsKey(entry.boothId()))
                .toList();

        //hallId 기준 그룹핑 (LinkedHashMap으로 등장 순서 보존, 순회 순서 안정성용)
        Map<Long, List<ClaudeBoothRecommender.RouteRecommendationEntry>> entriesByHallId = locatedEntries.stream()
                .collect(Collectors.groupingBy(
                        entry -> firstSlotByBoothId.get(entry.boothId()).getHallId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<HallRoute> hallRoutes = new ArrayList<>();
        for (Map.Entry<Long, List<ClaudeBoothRecommender.RouteRecommendationEntry>> hallGroup : entriesByHallId.entrySet()) {
            //그룹당 MAX_HALL_GROUP_SIZE(6개)로 제한. matched 우선 정렬 상태라 앞에서부터 자름
            List<ClaudeBoothRecommender.RouteRecommendationEntry> capped = hallGroup.getValue().stream()
                    .limit(MAX_HALL_GROUP_SIZE)
                    .toList();

            List<BoothSlotLocation> slotsInHall = capped.stream()
                    .map(entry -> firstSlotByBoothId.get(entry.boothId()))
                    .toList();
            List<BoothSlotLocation> orderedSlots = optimizeRouteOrder(slotsInHall);

            Map<Long, ClaudeBoothRecommender.RouteRecommendationEntry> entryByBoothId = capped.stream()
                    .collect(Collectors.toMap(ClaudeBoothRecommender.RouteRecommendationEntry::boothId, entry -> entry));

            List<BoothRouteItem> stops = new ArrayList<>();
            int order = 1;
            for (BoothSlotLocation slot : orderedSlots) {
                ClaudeBoothRecommender.RouteRecommendationEntry entry = entryByBoothId.get(slot.getBoothId());
                stops.add(new BoothRouteItem(
                        slot.getBoothId(),
                        nameByBoothId.get(slot.getBoothId()),
                        entry.reason(),
                        entry.matched(),
                        slot.getSlotNumber(),
                        order++
                ));
            }

            hallRoutes.add(new HallRoute(hallGroup.getKey(), slotsInHall.get(0).getHallName(), stops));
        }

        //홀 나열 순서는 오름차순
        hallRoutes.sort(Comparator.comparing(HallRoute::hallId));
        return hallRoutes;
    }

    //홀 하나 안에서, 완전탐색 순열로 총 이동거리가 최소인 방문 순서를 계산
    //그룹 크기가 항상 MAX_HALL_GROUP_SIZE(6) 이하로 캡돼 있어서 순열이 최대 6! = 720가지뿐이라
    //완전탐색으로 충분하다 (다만 입구/게이트 좌표가 시작점 고정할 수 없엉)
    private List<BoothSlotLocation> optimizeRouteOrder(List<BoothSlotLocation> slots) {
        if (slots.size() <= 1) {
            return slots;
        }

        BestRoute best = new BestRoute();
        boolean[] used = new boolean[slots.size()];
        List<BoothSlotLocation> path = new ArrayList<>(slots.size());

        searchBestRoute(slots, used, path, 0.0, best);

        return best.route;
    }

    //백트래킹으로 순열을 전부 시도하되, 이미 최선 기록보다 멀어진 경로는 그 자리에서 가지치기한다
    private void searchBestRoute(
            List<BoothSlotLocation> slots, boolean[] used, List<BoothSlotLocation> path,
            double distanceSoFar, BestRoute best
    ) {
        if (path.size() == slots.size()) {
            if (distanceSoFar < best.distance) {
                best.distance = distanceSoFar;
                best.route = new ArrayList<>(path);
            }
            return;
        }

        for (int i = 0; i < slots.size(); i++) {
            if (used[i]) {
                continue;
            }
            BoothSlotLocation next = slots.get(i);
            double addedDistance = path.isEmpty() ? 0.0 : distanceBetween(path.get(path.size() - 1), next);
            double newDistance = distanceSoFar + addedDistance;
            if (newDistance >= best.distance) {
                //여기서부터는 이어 붙여봐야 이미 찾은 최선보다 짧아질 수 없으니 더 안 내려감
                continue;
            }

            used[i] = true;
            path.add(next);
            searchBestRoute(slots, used, path, newDistance, best);
            path.remove(path.size() - 1);
            used[i] = false;
        }
    }

    private double distanceBetween(BoothSlotLocation a, BoothSlotLocation b) {
        double dx = a.getPosX().doubleValue() - b.getPosX().doubleValue();
        double dy = a.getPosY().doubleValue() - b.getPosY().doubleValue();
        return Math.sqrt(dx * dx + dy * dy);
    }

    //searchBestRoute()가 재귀 중에 지금까지 찾은 최선의 경로를 기록해두는 용도
    private static final class BestRoute {
        List<BoothSlotLocation> route;
        double distance = Double.MAX_VALUE;
    }
}
