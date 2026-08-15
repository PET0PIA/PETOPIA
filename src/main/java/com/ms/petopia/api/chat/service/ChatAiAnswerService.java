package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.config.ChatAsyncConfig;
import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import com.ms.petopia.api.chat.dto.ChatSenderType;
import com.ms.petopia.api.chat.entity.ChatMessage;
import com.ms.petopia.api.chat.event.ChatAiAnswerRequestedEvent;
import com.ms.petopia.api.chat.mapper.ChatConversationMapper;
import com.ms.petopia.api.chat.mapper.ChatMessageMapper;
import com.ms.petopia.api.chat.mapper.ChatSettingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;

/**
 * AI 답변을 실제로 만들어 붙인다.
 *
 * <p>{@code @TransactionalEventListener}라서 <b>사용자 메시지가 커밋된 뒤에</b> 실행되고,
 * {@code @Async}라서 사용자 요청을 붙잡지 않는다. 이 둘이 함께 있어야 "질문보다 답이 먼저
 * 보이는" 순서 역전과 "전송 버튼이 몇 초간 멈추는" 문제를 동시에 피할 수 있다.
 *
 * <p>슬롯 선점은 여기가 아니라 호출부({@link ChatConversationService})에서 이미 끝났다.
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

    private final ClaudeSupportResponder responder;
    private final ChatMessageWriter messageWriter;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatSettingMapper settingMapper;
    private final ChatTimeProvider timeProvider;

    /*
     * REQUIRES_NEW인 이유: 이 리스너는 원본 트랜잭션이 커밋된 뒤에 실행되므로 합류할
     * 트랜잭션이 없다(스프링이 아예 기동을 막는다). 여기서 하는 쓰기 - AI 메시지 저장과
     * 슬롯 반납 - 는 자기 트랜잭션으로 묶여야 한다.
     */
    @Async(ChatAsyncConfig.CHAT_AI_EXECUTOR)
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAiAnswerRequested(ChatAiAnswerRequestedEvent event) {
        Optional<String> answer = responder.answer(buildTranscript(event.conversationId()), event.aiContext());

        if (answer.isEmpty()) {
            // 답하지 않기로 했거나 호출이 실패했다. 선점해 둔 슬롯을 돌려줘야 남은 횟수가
            // 엉뚱하게 줄지 않는다 - 답을 못 받은 사용자가 한도만 잃는 상황을 막는다.
            conversationMapper.releaseAiAnswer(event.conversationId());
            return;
        }

        messageWriter.append(event.conversationId(), ChatSenderType.AI, null, null, answer.get());

        if (!event.lastAnswer()) {
            // 아직 여유가 있으면 잠그지 않는다. 사용자는 이어서 더 물어볼 수 있다.
            return;
        }

        // 마지막 답변이었다. 자동 답변이 끝났다는 사실과 사람 답변이 늦을 수 있다는 예고를
        // 남기고 잠근다. 안내 없이 잠그면 사용자는 입력창이 왜 막혔는지 알 수 없다.
        messageWriter.append(event.conversationId(), ChatSenderType.SYSTEM, null, null,
                setting(SETTING_AI_LIMIT_NOTICE));

        // 잠금은 여기가 유일한 지점이다. 전이에 실패하면(그 사이 상담사가 답했거나 대화가
        // 끝난 경우) 잠그지 않고, 화면에 알릴 상태 변화도 없으므로 이벤트도 보내지 않는다.
        if (conversationMapper.markAiAnswered(event.conversationId(), timeProvider.now()) == 1) {
            messageWriter.publishStatus(event.conversationId(), ChatConversationStatus.AI_ANSWERED);
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

    private String setting(String key) {
        String value = settingMapper.selectValue(key);
        // 문구가 비어 있어도 잠금 자체는 걸려야 한다. 다만 안내 없이 잠기면 사용자가
        // 이유를 알 수 없으므로 최소한의 기본 문구를 남긴다.
        return (value != null && !value.isBlank())
                ? value
                : "자동 답변은 여기까지예요. 상담사가 확인 후 답변드릴게요.";
    }
}
