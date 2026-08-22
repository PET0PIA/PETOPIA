package com.ms.petopia.api.recommendation.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.ms.petopia.api.pet.dto.PetAllergyResponse;
import com.ms.petopia.api.pet.dto.PetResponse;
import com.ms.petopia.api.recommendation.domain.BoothCandidate;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/*
 * Claude API(claude-haiku-4-5)를 호출해서 부스 추천을 받아오는 컴포넌트.
 * 클라이언트는 이 컴포넌트가 뜰 때 딱 한 번만 만들어서 재사용한다
 */
@Slf4j
@Component
public class ClaudeBoothRecommender {

    private static final String MODEL = "claude-haiku-4-5";
    private static final long MAX_TOKENS = 2000L;

    private static final String SYSTEM_PROMPT = """
            당신은 반려동물 박람회 부스 추천 도우미입니다.
            주어진 반려동물 정보(종, 나이, 크기 - 여러 마리일 수 있습니다)와(또는) 사용자가 찾는
            물건 설명을 참고해서, 아래 부스 목록 중 가장 적합한 부스를 최대 5개까지 골라 추천하세요.
            사용자가 구체적으로 원하는 물건을 적지 않았더라도, 반려동물 정보만으로 그 반려동물의
            종·특성에 어울리는 부스(대상동물이 그 종과 일치하거나 모든 동물 공용인 부스)를 폭넓게
            추천하세요 - "특별한 요청이 없다"는 이유로 빈 목록을 반환하지 마세요.
            목록에 없는 부스는 추천하지 마세요. 후보 부스 중 반려동물/요청과 관련 있는 부스가
            정말 하나도 없을 때만 빈 목록을 반환하세요.
            반려동물이 여러 마리면 reason에 어느 반려동물에게 맞는지 이름으로 언급하세요.
            reason은 한국어로 1~2문장으로 간결하게 작성하세요.
            반려동물에게 알레르기가 있으면 해당 성분을 다루는 사료·간식 부스는 추천하지 않거나 reason에 주의를 함께 적으세요. 알레르기가 없다고 확인된 경우에는 제약 없이 추천하세요.
            """;

    private final AnthropicClient client;
    private final boolean enabled;

    public ClaudeBoothRecommender(@Value("${anthropic.api-key}") String apiKey) {
        this.enabled = apiKey != null && !apiKey.isBlank();
        //키가 없으면 클라이언트 자체를 안 만든다 - 이 기능을 안 쓰는 로컬 환경에서 굳이 만들 필요 없음
        this.client = enabled ? AnthropicOkHttpClient.builder().apiKey(apiKey).build() : null;
    }

    public List<RecommendationEntry> recommend(List<PetResponse> pets, String need, List<BoothCandidate> candidates) {
        if (!enabled) {
            throw new CommonException(ErrorCode.AI_RECOMMENDATION_UNAVAILABLE);
        }

        StructuredMessageCreateParams<RecommendationResult> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .outputConfig(RecommendationResult.class)
                .addUserMessage(buildUserPrompt(pets, need, candidates))
                .build();

        try {
            RecommendationResult result = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(structuredText -> structuredText.text())
                    .orElseThrow(() -> new CommonException(ErrorCode.AI_RECOMMENDATION_UNAVAILABLE));
            return result.recommendations();
        } catch (AnthropicServiceException | AnthropicIoException e) {
            log.error("Claude API 호출 실패", e);
            throw new CommonException(ErrorCode.AI_RECOMMENDATION_UNAVAILABLE, e);
        }
    }

    //동선 추천용 시스템 프롬프트
    private static final String ROUTE_SYSTEM_PROMPT = """
            당신은 반려동물 박람회 부스 추천 도우미입니다. 이번 추천은 관람객이 실제로 걸어서
            돌아볼 "동선"을 만드는 용도이므로, 딱 맞는 부스가 적더라도 가능하면 최소 3~4개 이상을
            추천해서 여러 곳을 도는 경로가 되게 하세요(후보 부스 자체가 그보다 적으면 있는 만큼만).
            주어진 반려동물 정보(종, 나이, 크기 - 여러 마리일 수 있습니다)와(또는) 사용자가 찾는
            물건 설명을 참고해서, 아래 부스 목록 중 가장 적합한 부스를 최대 8개(matched=true)
            고르고, 부족하면 그 외 겸사겸사 둘러볼만한 부스를 최대 3개(matched=false)까지 추가해서
            채우세요. 사용자가 구체적으로 원하는 물건을 적지 않았더라도, 반려동물 정보만으로 그
            반려동물의 종·특성에 어울리는 부스(대상동물이 그 종과 일치하거나 모든 동물 공용인
            부스)를 폭넓게 추천하세요 - "특별한 요청이 없다"는 이유로 빈 목록을 반환하지 마세요.
            목록에 없는 부스는 추천하지 마세요. 후보 부스 중 반려동물/요청과 관련 있는 부스가
            정말 하나도 없을 때만 빈 목록을 반환하세요.
            반려동물이 여러 마리면 reason에 어느 반려동물에게 맞는지 이름으로 언급하세요.
            reason은 한국어로 1~2문장으로 간결하게 작성하세요.
            반려동물에게 알레르기가 있으면 해당 성분을 다루는 사료·간식 부스는 추천하지 않거나 reason에 주의를 함께 적으세요. 알레르기가 없다고 확인된 경우에는 제약 없이 추천하세요.
            """;

    //동선 추천용 Claude 호출. recommend()와 구조는 같고, 결과에 matched(맞춤/추가 구분)가 붙는 버전
    public List<RouteRecommendationEntry> recommendForRoute(List<PetResponse> pets, String need, List<BoothCandidate> candidates) {
        if (!enabled) {
            throw new CommonException(ErrorCode.AI_RECOMMENDATION_UNAVAILABLE);
        }

        StructuredMessageCreateParams<RouteRecommendationResult> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(ROUTE_SYSTEM_PROMPT)
                .outputConfig(RouteRecommendationResult.class)
                .addUserMessage(buildUserPrompt(pets, need, candidates))
                .build();

        try {
            RouteRecommendationResult result = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(structuredText -> structuredText.text())
                    .orElseThrow(() -> new CommonException(ErrorCode.AI_RECOMMENDATION_UNAVAILABLE));
            return result.recommendations();
        } catch (AnthropicServiceException | AnthropicIoException e) {
            log.error("Claude API 호출 실패", e);
            throw new CommonException(ErrorCode.AI_RECOMMENDATION_UNAVAILABLE, e);
        }
    }

    private String buildUserPrompt(List<PetResponse> pets, String need, List<BoothCandidate> candidates) {
        StringBuilder sb = new StringBuilder();

        //PetResponse는 record라 getXxx()가 아니라 xxx()로 값을 꺼낸다(레코드 접근자 이름 규칙)
        //여러 마리를 한 번에 보낼 수 있어서 번호를 붙여 나열한다 - 어느 부스가 어느 반려동물에게
        //맞는지 Claude가 구분할 수 있게 각 부스의 reason에서 이름을 언급하도록 시스템 프롬프트에서도 안내한다.
        if (pets != null && !pets.isEmpty()) {
            sb.append("반려동물 정보:\n");
            for (PetResponse pet : pets) {
                sb.append("- ").append(pet.name()).append(": ")
                        .append(pet.species());
                if (pet.breed() != null && !pet.breed().isBlank()) {
                    sb.append(", ").append(pet.breed());
                }
                if (pet.birthDate() != null) {
                    int age = Period.between(pet.birthDate(), LocalDate.now()).getYears();
                    sb.append(", ").append(age).append("살");
                }
                if ("MALE".equals(pet.gender())) {
                    sb.append(", 수컷");
                } else if ("FEMALE".equals(pet.gender())) {
                    sb.append(", 암컷");
                }
                if (Boolean.TRUE.equals(pet.isNeutered())) {
                    sb.append(", 중성화 완료");
                }
                appendAllergyInfo(sb, pet);
                sb.append("\n");
            }
        }

        if (need != null && !need.isBlank()) {
            sb.append("사용자 요청: ").append(need).append("\n");
        }

        sb.append("부스 목록:\n");
        for (BoothCandidate candidate : candidates) {
            sb.append("- [").append(candidate.getBoothId()).append("] ")
                    .append(candidate.getName())
                    .append(" (카테고리: ").append(candidate.getCategory())
                    .append(", 대상동물: ").append(candidate.getTargetAnimal()).append(")\n")
                    .append("  소개: ").append(candidate.getIntro()).append("\n");
            if (candidate.getItems() != null && !candidate.getItems().isEmpty()) {
                String items = candidate.getItems().stream()
                        .map(item -> item.getNote() != null
                                ? item.getName() + "(" + item.getNote() + ")"
                                : item.getName())
                        .collect(Collectors.joining(", "));
                sb.append("  판매상품: ").append(items).append("\n");
            }
        }

        return sb.toString();
    }

    private void appendAllergyInfo(StringBuilder sb, PetResponse pet) {
        if (Boolean.FALSE.equals(pet.hasAllergy())) {
            sb.append(", 알레르기 없음");
            return;
        }
        if (!Boolean.TRUE.equals(pet.hasAllergy()) || pet.allergies() == null || pet.allergies().isEmpty()) {
            return;
        }

        StringJoiner allergyNames = new StringJoiner("·");
        for (PetAllergyResponse allergy : pet.allergies()) {
            if (allergy == null) {
                continue;
            }
            String name = "OTHER".equals(allergy.code()) ? allergy.otherText() : allergy.label();
            if (name != null && !name.isBlank()) {
                allergyNames.add(name);
            }
        }
        if (allergyNames.length() > 0) {
            sb.append(", 알레르기: ").append(allergyNames);
        }
    }

    @JsonClassDescription("부스 추천 결과")
    record RecommendationResult(
            @JsonPropertyDescription("추천하는 부스 목록, 최대 5개, 적합한 부스가 없으면 빈 배열")
            List<RecommendationEntry> recommendations
    ) {
    }

    record RecommendationEntry(
            @JsonPropertyDescription("추천하는 부스의 booth_id")
            Long boothId,
            @JsonPropertyDescription("이 부스를 추천하는 이유, 한국어 1~2문장")
            String reason
    ) {
    }

    @JsonClassDescription("동선 추천 결과")
    record RouteRecommendationResult(
            @JsonPropertyDescription("추천하는 부스 목록. matched 최대 8개 + extra 최대 3개, 총 최대 11개, 적합한 부스가 없으면 빈 배열")
            List<RouteRecommendationEntry> recommendations
    ) {
    }

    record RouteRecommendationEntry(
            @JsonPropertyDescription("추천하는 부스의 booth_id")
            Long boothId,
            @JsonPropertyDescription("이 부스를 추천하는 이유, 한국어 1~2문장")
            String reason,
            @JsonPropertyDescription("반려동물/요구조건에 딱 맞아서 추천했으면 true(matched), 그 외 겸사겸사 둘러볼만해서 추천했으면 false(extra)")
            boolean matched
    ) {
    }
}
