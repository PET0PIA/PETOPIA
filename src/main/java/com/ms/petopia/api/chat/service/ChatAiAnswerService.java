package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import com.ms.petopia.api.chat.dto.ChatSenderType;
import com.ms.petopia.api.chat.entity.ChatConversation;
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
 * 스프링 프록시를 거치지 않아 {@code @Transactional}이 조용히 무시된다. 그러면 답변이
 * 롤백될 때 선점 반납까지 함께 취소돼, 정작 반납이 필요한 실패 경로에서 동작하지 않는다.
 * 그래서 "끝나면 반납한다"는 흐름은 별도 빈인 리스너가 조립한다.
 *
 * <p>호출 선점은 여기가 아니라 {@link ChatConversationService}에서 이미 끝났다.
 * 여기서 선점하면 비동기 실행이 밀리는 사이 같은 대화에 두 번째 질문이 들어와 호출이 겹친다.
 *
 * <p><b>답변 횟수 한도는 없다.</b> 사람이 답할 수 없는 시간에 답을 아끼는 것은 아낄 이유가
 * 없는 절약이었다. 남은 제약은 "대화당 진행 중 호출 1건"뿐이고, 그건 한도가 아니라 중복
 * 제거다 - 자세한 이유는 {@link ChatConversationMapper#claimAiCall}에 적었다.
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

    /**
     * AI 답변 끝에 서버가 덧붙이는 안내.
     *
     * <p>프롬프트로 부탁하지 않고 서버가 붙이는 이유: 프롬프트 지시는 붙는 날도 있고 안 붙는
     * 날도 있다. 운영자가 문구를 고칠 수 있어야 한다는 요구까지 겹치면 설정값 + 서버 append가
     * 유일하게 확정적인 방법이다.
     */
    private static final String SETTING_AI_CLOSING_NOTE = "AI_CLOSING_NOTE";
    private static final String DEFAULT_AI_CLOSING_NOTE =
            "더 상세한 답변이 필요하시면 운영시간에 다시 문의해주세요.";

    /** 프롬프트에 실을 도메인 지식. 메뉴가 아니라 전역이다 - AI가 특정 버튼에 붙지 않는다. */
    private static final String SETTING_AI_CONTEXT = "AI_CONTEXT";

    private static final String SETTING_AI_ESCALATE_NOTICE = "AI_ESCALATE_NOTICE";
    private static final String DEFAULT_AI_ESCALATE_NOTICE =
            "이 문의는 상담사가 확인하는 편이 정확해요. 남겨주신 내용은 전달했고, 확인 후 이 창으로 답변드릴게요.";

    private final ClaudeSupportResponder responder;
    private final ChatMessageWriter messageWriter;
    private final ChatConversationMapper conversationMapper;
    private final ChatMessageMapper messageMapper;
    private final ChatSettingMapper settingMapper;
    private final ChatTimeProvider timeProvider;

    /**
     * 진행 중 호출 선점 반납.
     *
     * <p>답변 트랜잭션이 롤백돼도 반납은 남아야 하므로 트랜잭션을 따로 연다. 반납되지 않은
     * 선점은 stale 창(3분)이 지나면 스스로 풀리지만, 그때까지는 그 대화의 자동 응대가 막힌다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseCall(Long conversationId) {
        try {
            conversationMapper.releaseAiCall(conversationId);
        } catch (RuntimeException e) {
            // 반납까지 실패해도 stale 창이 지나면 풀린다. 여기서 더 할 수 있는 일은 없고,
            // 그 3분을 설명할 수 있게 남긴다.
            log.error("AI 호출 선점 반납 실패. stale 창이 지나면 풀린다. conversationId={}",
                    conversationId, e);
        }
    }

    /**
     * 답변을 만들어 붙인다.
     *
     * <p>답변을 얻은 뒤 곧바로 저장하지 않는다. {@link ChatConversationMapper#markAiHandled}의
     * 조건부 전이를 먼저 통과해야 한다 - 그 전이가 이 트랜잭션의 저장 권한이다.
     *
     * @return 실제로 AI 답변을 남겼는지. 반납은 성공·실패 모두에서 필요하므로 이 값으로
     *         반납을 판단하지 않는다 - 호출자가 로그와 이관 안내를 가르는 데 쓴다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryAnswer(ChatAiAnswerRequestedEvent event) {
        Optional<String> answer = responder.answer(
                event.conversationId(), buildTranscript(event.conversationId()), aiContext());

        if (answer.isEmpty()) {
            // 답하지 않기로 했거나(에스컬레이션) 호출이 실패했다. 여기서 조용히 끝내면
            // 사용자는 입력창이 열려 있는데 답도 안 오는 상태에 남는다 - 왜 기다려야 하는지
            // 모르는 채 같은 질문을 반복한다. 그래서 이유를 남긴다.
            appendEscalateNotice(event.conversationId());
            return false;
        }

        // 조건부 전이를 저장 권한으로 쓴다. 순서가 반대였을 때(붙인 뒤에 전이) 전이가 실패한
        // 경우 - 그 사이 상담사가 답했거나 사용자가 종료한 경우 - 답변만 남았다. 사람이
        // 이어받은 상담에 자동 답변이 뒤늦게 끼어들고, 끝낸 창에 말풍선이 하나 더 붙는다.
        // 되돌릴 수 없는 쓰기 앞에 판정을 두면 그 창이 닫힌다.
        //
        // 이 순서는 stale 창을 넘겨 겹친 두 작업의 중복 답변도 함께 막는다. 먼저 도착한 쪽이
        // AI_HANDLED로 넘기면 뒤이은 쪽은 여기서 0을 받고 자기 답변을 버린다.
        if (conversationMapper.markAiHandled(event.conversationId(), timeProvider.now()) != 1) {
            log.info("AI 답변을 저장하지 않는다. 그 사이 상담사가 답했거나 대화가 끝났다. conversationId={}",
                    event.conversationId());
            return false;
        }

        // 답변과 마무리 안내를 한 말풍선으로 묶는다. 따로 붙이면 짧은 안내가 독립 메시지로
        // 쌓여, 몇 번 주고받은 뒤에는 창의 절반이 같은 안내로 채워진다.
        String body = answer.get() + "\n\n" + setting(SETTING_AI_CLOSING_NOTE, DEFAULT_AI_CLOSING_NOTE);
        messageWriter.append(event.conversationId(), ChatSenderType.AI, null, null, body);

        conversationMapper.incrementAiAnswerCount(event.conversationId());
        messageWriter.publishStatus(event.conversationId(), ChatConversationStatus.AI_HANDLED);
        return true;
    }

    /**
     * 이관 안내만 붙인다. 큐 거부처럼 {@link #tryAnswer}에 들어가지도 못한 경로에서 쓴다.
     *
     * <p>그 경로는 답변도 없고 안내도 없어서, 사용자에게는 "밤에 아무 일도 일어나지 않은"
     * 화면으로 보인다. 상태는 {@code WAITING_AGENT}로 남아 대기열에 들어가므로 상담은
     * 이어지는데, 그 사실을 알 방법이 없다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void escalateWithoutAnswer(Long conversationId) {
        try {
            appendEscalateNotice(conversationId);
        } catch (RuntimeException e) {
            log.error("이관 안내를 남기지 못했다. conversationId={}", conversationId, e);
        }
    }

    /**
     * 답변을 남기지 못했다는 사실과 그래서 무엇을 기다리면 되는지 알린다.
     *
     * <p>같은 안내가 이미 있으면 붙이지 않는다. 이관 판정은 연달아 나오기 쉬운데(같은 주제를
     * 다시 물으면 같은 판정이 나온다) 그때마다 같은 문장을 쌓으면 창이 안내로만 채워져,
     * 정작 뒤에 붙을 상담사 답변이 묻힌다.
     *
     * <p>확인 전에 대화 행을 잠그는 이유는 {@link ChatConversationMapper#lockById}에 적었다.
     * 요약하면 같은 대화의 AI 작업이 둘 동시에 돌 수 있어(stale 창을 넘긴 재선점), 잠그지
     * 않으면 둘 다 "없다"로 읽고 둘 다 붙인다.
     *
     * <p><b>여전히 상담사를 기다리는 대화에만 붙인다.</b> 이 문장은 "상담사가 확인 후
     * 답변드릴게요"라고 약속하는데, 그 사이 상담사가 이미 답했거나(IN_PROGRESS) 사용자가
     * 종료했으면(CLOSED) 사실이 아닌 말이 남는다. 답변 경로와 같은 규칙이다 - 쓰기 전에
     * 상태를 확인한다.
     */
    private void appendEscalateNotice(Long conversationId) {
        String notice = setting(SETTING_AI_ESCALATE_NOTICE, DEFAULT_AI_ESCALATE_NOTICE);
        conversationMapper.lockById(conversationId);

        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null || !conversation.getStatus().needsAgentReply()) {
            return;
        }

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

    /**
     * 프롬프트에 실을 도메인 지식.
     *
     * <p>메뉴별 값이 아니라 전역 설정에서 읽는다. AI가 더 이상 특정 버튼에 붙지 않으므로
     * 참고할 메뉴가 없다 - 상담원 연결 하나에 모든 주제가 들어온다.
     *
     * <p>비어 있어도 호출을 막지 않는다. 참고 정보가 없으면 응답기가 대부분 이관을 택하고,
     * 그 판정은 이 클래스가 아니라 프롬프트가 할 일이다.
     */
    private String aiContext() {
        String value = settingMapper.selectValue(SETTING_AI_CONTEXT);
        return value != null ? value : "";
    }

    private String setting(String key, String fallback) {
        String value = settingMapper.selectValue(key);
        // 문구가 비어 있어도 잠금이나 이관 자체는 진행돼야 한다. 다만 안내 없이 잠기거나
        // 조용히 넘어가면 사용자가 이유를 알 수 없으므로 최소한의 기본 문구를 남긴다.
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
