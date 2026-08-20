package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import com.ms.petopia.api.chat.dto.ChatSenderType;
import com.ms.petopia.api.chat.entity.ChatMessage;
import com.ms.petopia.api.chat.event.ChatAiAnswerRequestedEvent;
import com.ms.petopia.api.chat.mapper.ChatConversationMapper;
import com.ms.petopia.api.chat.mapper.ChatMessageMapper;
import com.ms.petopia.api.chat.mapper.ChatSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * AI 답변을 실제로 만들어 붙인다. {@link ChatAiAnswerListener}가 전용 스레드 풀에서 호출한다.
 *
 * <p><b>두 메서드를 한 클래스 안에서 서로 부르지 않는다.</b> 같은 빈 안에서 this로 호출하면
 * 스프링 프록시를 거치지 않아 {@code @Transactional}이 조용히 무시된다. 그러면 실패 시
 * 한도 반납이 답변 트랜잭션과 함께 롤백돼, 사용자가 답변도 못 받고 횟수만 잃는다.
 * 그래서 "실패하면 반납한다"는 흐름은 별도 빈인 리스너가 조립한다.
 *
 * <p>슬롯 선점은 여기가 아니라 {@link ChatConversationService}에서 이미 끝났다.
 * 여기서 선점하면 비동기 실행이 밀리는 사이 같은 대화에 두 번째 질문이 들어와 한도를 넘길 수 있다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAiAnswerService {

    /**
     * 프롬프트에 실을 최근 메시지 수.
     *
     * <p>전부 싣지 않는 이유는 비용과 정확도 양쪽이다. 상담은 보통 몇 번 주고받고 끝나므로
     * 최근 몇 건이면 문맥이 충분하고, 오래된 내용까지 넣으면 이미 끝난 주제를 다시 답한다.
     */
    private static final int TRANSCRIPT_SIZE = 12;

    private static final String SETTING_AI_LIMIT_NOTICE = "AI_LIMIT_NOTICE";
    private static final String DEFAULT_AI_LIMIT_NOTICE =
            "자동 답변은 여기까지예요. 상담사가 확인 후 답변드릴게요.";

    private static final String SETTING_AI_ESCALATE_NOTICE = "AI_ESCALATE_NOTICE";
    private static final String DEFAULT_AI_ESCALATE_NOTICE =
            "이 문의는 상담사가 확인하는 편이 정확해요. 남겨주신 내용은 전달했고, 확인 후 이 창으로 답변드릴게요.";

    private final ClaudeSupportResponder responder;
    private final ChatMessageWriter messageWriter;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatSettingMapper settingMapper;
    private final ChatTimeProvider timeProvider;

    /** 선점 슬롯 반납. 답변 트랜잭션이 롤백돼도 반납은 남아야 하므로 트랜잭션을 따로 연다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseSlot(Long conversationId) {
        try {
            conversationMapper.releaseAiAnswer(conversationId);
        } catch (RuntimeException e) {
            // 반납까지 실패하면 그 대화는 한 번의 기회를 잃는다. 상담은 상담사 대기로 이어지므로
            // 여기서 더 할 수 있는 일은 없고, 추적할 수 있게 남긴다.
            log.error("AI 한도 반납 실패. conversationId={}", conversationId, e);
        }
    }

    /** @return 실제로 AI 답변을 남겼는지. 거짓이면 호출자가 한도를 되돌린다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAnswer(ChatAiAnswerRequestedEvent event) {
        Optional<String> answer = responder.answer(
                event.conversationId(), buildTranscript(event.conversationId()), event.aiContext());

        if (answer.isEmpty()) {
            // 답하지 않기로 했거나(에스컬레이션) 호출이 실패했다. 여기서 조용히 끝내면
            // 사용자는 입력창이 열려 있는데 답도 안 오는 상태에 남는다 - 왜 기다려야 하는지
            // 모르는 채 같은 질문을 반복하다 한도를 다 쓴다. 그래서 이유를 남긴다.
            appendEscalateNotice(event.conversationId());
            return false;
        }

        messageWriter.append(event.conversationId(), ChatSenderType.AI, null, null, answer.get());

        if (!event.lastAnswer()) {
            // 아직 여유가 있으면 잠그지 않는다. 사용자는 이어서 더 물어볼 수 있다.
            return true;
        }

        // 마지막 답변이었다. 자동 답변이 끝났다는 사실과 사람 답변이 늦을 수 있다는 예고를
        // 남기고 잠근다. 안내 없이 잠그면 사용자는 입력창이 왜 막혔는지 알 수 없다.
        messageWriter.append(event.conversationId(), ChatSenderType.SYSTEM, null, null,
                setting(SETTING_AI_LIMIT_NOTICE, DEFAULT_AI_LIMIT_NOTICE));

        // 잠금은 여기가 유일한 지점이다. 전이에 실패하면(그 사이 상담사가 답했거나 대화가
        // 끝난 경우) 잠그지 않고, 화면에 알릴 상태 변화도 없으므로 이벤트도 보내지 않는다.
        if (conversationMapper.markAiAnswered(event.conversationId(), timeProvider.now()) == 1) {
            messageWriter.publishStatus(event.conversationId(), ChatConversationStatus.AI_ANSWERED);
        }
        return true;
    }

    /**
     * 답변을 남기지 못했다는 사실과 그래서 무엇을 기다리면 되는지 알린다.
     *
     * <p>같은 안내가 이미 있으면 붙이지 않는다. 이관 판정은 연달아 나오기 쉬운데(같은 주제를
     * 다시 물으면 같은 판정이 나온다) 그때마다 같은 문장을 쌓으면 창이 안내로만 채워져,
     * 정작 뒤에 붙을 상담사 답변이 묻힌다.
     *
     * <p>확인 전에 대화 행을 잠그는 이유는 {@link ChatConversationMapper#lockById}에 적었다.
     * 요약하면 같은 대화의 AI 작업이 둘 동시에 돌 수 있어, 잠그지 않으면 둘 다 "없다"로
     * 읽고 둘 다 붙인다.
     */
    private void appendEscalateNotice(Long conversationId) {
        String notice = setting(SETTING_AI_ESCALATE_NOTICE, DEFAULT_AI_ESCALATE_NOTICE);
        conversationMapper.lockById(conversationId);
        if (!messageMapper.existsSystemMessage(conversationId, notice)) {
            messageWriter.append(conversationId, ChatSenderType.SYSTEM, null, null, notice);
        }
    }

    /**
     * 최근 대화를 프롬프트용 줄 목록으로 만든다.
     *
     * <p>SYSTEM 메시지(접수 안내·운영시간 안내)는 뺀다. 대화 내용이 아니라 화면 안내라
     * 넣어봐야 AI가 답할 거리가 아니고, 오히려 그걸 사용자 발화로 오해할 수 있다.
     */
    private List<String> buildTranscript(Long conversationId) {
        List<ChatMessage> messages =
                messageMapper.selectRecentByConversation(conversationId, TRANSCRIPT_SIZE);
        return messages.stream()
                .filter(m -> m.getSenderType() != ChatSenderType.SYSTEM)
                .map(m -> switch (m.getSenderType()) {
                    case USER -> "고객: " + m.getContent();
                    case AGENT -> "상담사: " + m.getContent();
                    default -> "자동답변: " + m.getContent();
                })
                .toList();
    }

    private String setting(String key, String fallback) {
        String value = settingMapper.selectValue(key);
        // 문구가 비어 있어도 잠금이나 이관 자체는 진행돼야 한다. 다만 안내 없이 잠기거나
        // 조용히 넘어가면 사용자가 이유를 알 수 없으므로 최소한의 기본 문구를 남긴다.
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
