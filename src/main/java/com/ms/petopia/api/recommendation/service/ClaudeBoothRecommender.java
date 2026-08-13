package com.ms.petopia.api.recommendation.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
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
            주어진 반려동물 정보(종, 나이, 크기)와(또는) 사용자가 찾는 물건 설명을 참고해서,
            아래 부스 목록 중 가장 적합한 부스를 최대 5개까지 골라 추천하세요.
            목록에 없는 부스는 추천하지 마세요. 추천할 부스가 하나도 없으면 빈 목록을 반환하세요.
            reason은 한국어로 1~2문장으로 간결하게 작성하세요.
            """;

    private final AnthropicClient client;
    private final boolean enabled;

    public ClaudeBoothRecommender(@Value("${anthropic.api-key}") String apiKey) {
        this.enabled = apiKey != null && !apiKey.isBlank();
        //키가 없으면 클라이언트 자체를 안 만든다 - 이 기능을 안 쓰는 로컬 환경에서 굳이 만들 필요 없음
        this.client = enabled ? AnthropicOkHttpClient.builder().apiKey(apiKey).build() : null;
    }

    public List<RecommendationEntry> recommend(PetResponse pet, String need, List<BoothCandidate> candidates) {
        if (!enabled) {
            throw new CommonException(ErrorCode.AI_RECOMMENDATION_UNAVAILABLE);
        }

        StructuredMessageCreateParams<RecommendationResult> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .outputConfig(RecommendationResult.class)
                .addUserMessage(buildUserPrompt(pet, need, candidates))
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

    //반려동물 정보 + 사용자 요청 + 부스 후보 목록을 텍스트로 조립
    private String buildUserPrompt(PetResponse pet, String need, List<BoothCandidate> candidates) {
        StringBuilder sb = new StringBuilder();

        //PetResponse는 record라 getXxx()가 아니라 xxx()로 값을 꺼낸다(레코드 접근자 이름 규칙)
        if (pet != null) {
            int age = Period.between(pet.birthDate(), LocalDate.now()).getYears();
            sb.append("반려동물 정보: ")
                    .append(pet.species()).append(", ")
                    .append(pet.breed()).append(", ")
                    .append(age).append("살, ")
                    .append("MALE".equals(pet.gender()) ? "수컷" : "암컷");
            if (Boolean.TRUE.equals(pet.isNeutered())) {
                sb.append(", 중성화 완료");
            }
            sb.append("\n");
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
}
