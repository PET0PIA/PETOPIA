package com.ms.petopia.api.chat.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 운영시간 밖에 들어온 문의에 Claude로 답변한다.
 *
 * <p>{@code ClaudeBoothRecommender}와 같은 구조다 - API 키가 없으면 클라이언트 자체를 만들지
 * 않고, 호출 실패는 예외로 흘리지 않는다.
 *
 * <p>이 컴포넌트의 핵심 설계는 <b>모르면 답하지 않는 것</b>이다. 상담 답변은 브랜드가 하는 말로
 * 읽히므로, 그럴듯한 추측이 정답보다 훨씬 비싸다. 확신이 없으면 {@code escalate=true}로
 * 돌려보내 상담사에게 넘긴다.
 */
@Slf4j
@Component
public class ClaudeSupportResponder {

    /**
     * 상담 답변 품질이 그대로 브랜드에 노출되므로 최신 모델을 쓴다.
     * 비용을 우선한다면 부스 추천이 쓰는 {@code claude-haiku-4-5}로 내릴 수 있다 - 이 상수
     * 한 줄만 바꾸면 되도록 다른 곳에 모델 이름을 흘리지 않았다.
     */
    private static final String MODEL = "claude-opus-5";

    /** 상담 답변은 3~5문장이면 충분하다. 길면 오히려 안 읽힌다. */
    private static final long MAX_TOKENS = 1024L;

    private static final String SYSTEM_PROMPT = """
            당신은 반려동물 박람회 플랫폼 '펫토피아'의 상담 도우미입니다.
            지금은 상담사 운영시간이 아니어서, 상담사를 대신해 당신이 답변합니다.

            규칙:
            - 한국어 존댓말로, 3~5문장 이내로 간결하게 답하세요.
            - 주어진 참고 정보에 없는 내용은 절대 지어내지 마세요. 특히 날짜, 금액, 장소,
              환불 규정처럼 사실 확인이 필요한 값은 추측하지 마세요.
            - 다음 경우에는 답변을 시도하지 말고 escalate를 true로 설정하세요:
              환불·결제·정산 관련, 개인정보 변경, 계정 문제, 참고 정보로 답할 수 없는 질문,
              사용자가 화가 나 있거나 사과가 필요한 상황.
            - escalate가 true이면 answer는 빈 문자열로 두세요. 상담사 연결 안내는 시스템이 붙입니다.
            - 답변 끝에 "상담사에게 문의하세요"나 "운영시간에 다시 문의해주세요" 같은 안내를
              덧붙이지 마세요. 시스템이 답변 뒤에 그 문장을 붙이므로 중복됩니다.
            - 앞선 대화에서 이미 답한 내용을 그대로 반복하지 말고, 마지막 질문에만 답하세요.
              이어지는 질문("그럼 그건요?")은 앞 문맥을 이어서 이해하세요.
            - 답변 횟수 제한은 없습니다. 남은 횟수나 자동 답변 종료를 언급하지 마세요.
            """;

    private final AnthropicClient client;
    private final boolean enabled;

    public ClaudeSupportResponder(@Value("${anthropic.api-key}") String apiKey) {
        this.enabled = apiKey != null && !apiKey.isBlank();
        // 키가 없으면 클라이언트를 만들지 않는다 - 이 기능을 안 쓰는 로컬 환경에서 굳이 만들 필요 없음
        this.client = enabled ? AnthropicOkHttpClient.builder().apiKey(apiKey).build() : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @param conversationId 로그 상관키. 답을 남기지 않은 경우 화면에는 아무 흔적이 없으므로,
     *                       어느 상담에서 왜 그랬는지는 로그로만 되짚을 수 있다.
     * @param transcript 지금까지의 대화(오래된 순). 마지막 줄이 이번에 답할 질문이다.
     *                   한 번만 답하던 시절엔 질문 한 줄이면 됐지만, 여러 번 주고받게 되면
     *                   "그럼 그건요?" 같은 이어지는 질문이 나온다. 앞선 문답을 함께 주지
     *                   않으면 그 질문에 엉뚱한 답이 붙는다.
     * @return 답변 본문. 답하지 않기로 했거나(escalate) 호출에 실패하면 빈 값 -
     *         호출자는 이 경우 상담사 대기 상태를 그대로 유지한다.
     */
    public Optional<String> answer(Long conversationId, List<String> transcript, String aiContext) {
        if (!enabled) {
            return Optional.empty();
        }

        StructuredMessageCreateParams<SupportAnswer> params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .system(SYSTEM_PROMPT)
                .outputConfig(SupportAnswer.class)
                .addUserMessage(buildUserPrompt(transcript, aiContext))
                .build();

        try {
            SupportAnswer result = client.messages().create(params).content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(structuredText -> structuredText.text())
                    .orElse(null);

            // 세 경우를 나눠 남긴다. 밖에서는 모두 "빈 값"으로 같아 보이지만, 이관은 정상
            // 동작이고 나머지 둘은 고쳐야 할 신호다. 뭉쳐두면 답이 없었던 이유를 알 수 없다.
            if (result == null) {
                log.warn("상담 AI 응답에 본문 블록이 없다. 상담사 대기로 넘긴다. conversationId={}",
                        conversationId);
                return Optional.empty();
            }
            if (result.escalate()) {
                log.info("상담 AI가 상담사 이관을 선택했다. conversationId={}", conversationId);
                return Optional.empty();
            }
            if (result.answer() == null || result.answer().isBlank()) {
                log.warn("상담 AI가 이관 없이 빈 답변을 냈다. 상담사 대기로 넘긴다. conversationId={}",
                        conversationId);
                return Optional.empty();
            }
            return Optional.of(result.answer().trim());
        } catch (AnthropicServiceException | AnthropicIoException e) {
            // 예외를 위로 던지지 않는다. AI는 어디까지나 덤이고, 실패하더라도 문의는 이미
            // 상담사 대기열에 들어가 있다. 사용자에게 오류를 보여줄 이유가 없다.
            log.warn("상담 AI 답변 실패. 상담사 대기로 넘긴다. conversationId={}", conversationId, e);
            return Optional.empty();
        }
    }

    private String buildUserPrompt(List<String> transcript, String aiContext) {
        StringBuilder sb = new StringBuilder();
        if (aiContext != null && !aiContext.isBlank()) {
            sb.append("참고 정보:\n").append(aiContext).append("\n\n");
        }
        sb.append("지금까지의 대화:\n");
        transcript.forEach(line -> sb.append(line).append("\n"));
        sb.append("\n위 대화의 마지막 고객 질문에 답하세요.");
        return sb.toString();
    }

    @JsonClassDescription("상담 자동 답변 결과")
    record SupportAnswer(
            @JsonPropertyDescription("고객에게 보여줄 답변. escalate가 true이면 빈 문자열")
            String answer,
            @JsonPropertyDescription("답변하지 않고 상담사에게 넘겨야 하면 true")
            boolean escalate
    ) {
    }
}
