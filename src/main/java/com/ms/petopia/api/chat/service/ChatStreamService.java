package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.entity.ChatConversation;
import com.ms.petopia.api.chat.mapper.ChatConversationMapper;
import com.ms.petopia.api.chat.sse.ChatEmitterRegistry;
import com.ms.petopia.api.chat.sse.ChatEventPublisher;
import com.ms.petopia.api.chat.sse.ChatStreamEvent;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Optional;

/**
 * 고객 위젯의 실시간 채널.
 *
 * <p>연결 자체에는 인증 헤더가 붙지 않으므로(EventSource 제약 - {@link ChatStreamTicketStore}
 * 참고), 소유 검증은 <b>티켓 발급 시점</b>과 <b>티켓 소비 시점</b> 두 번 이뤄진다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatStreamService {

    private final ChatConversationMapper conversationMapper;
    private final ChatStreamTicketStore ticketStore;
    private final ChatTypingStore typingStore;
    private final ChatEmitterRegistry emitterRegistry;
    private final ChatEventPublisher eventPublisher;

    /** 헤더로 소유를 증명한 요청에만 티켓을 내준다. */
    @Transactional(readOnly = true)
    public String issueTicket(Long conversationId, Long userId, String guestKey) {
        ChatConversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new CommonException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
        }
        if (!conversation.isOwnedBy(userId, guestKey)) {
            throw new CommonException(ErrorCode.CHAT_ACCESS_DENIED);
        }
        return ticketStore.issue(conversationId);
    }

    /**
     * 티켓을 소비하고 연결을 연다.
     *
     * <p>티켓이 가리키는 대화와 URL의 대화가 같은지 확인한다. 확인하지 않으면 자기 대화의
     * 티켓으로 남의 대화 스트림을 열 수 있다.
     *
     * <p>거부를 예외가 아니라 빈 값으로 돌려주는 이유: 이 엔드포인트를 호출하는 EventSource는
     * 항상 {@code Accept: text/event-stream}을 보내므로 JSON 에러 본문을 협상할 수 없다.
     * 예외를 던지면 핸들러가 응답을 쓰지 못해 500과 스택트레이스만 남는다. 어차피 EventSource는
     * 본문을 읽지 못하고 onerror만 받으므로, 상태 코드로만 답하는 편이 정확하다.
     *
     * @return 티켓이 유효하지 않으면 빈 값
     */
    public Optional<SseEmitter> subscribe(Long conversationId, String ticket) {
        Long ticketConversationId = ticketStore.consume(ticket);
        if (ticketConversationId == null || !ticketConversationId.equals(conversationId)) {
            return Optional.empty();
        }

        SseEmitter emitter = emitterRegistry.register(conversationId);

        // 지금 상담사가 입력 중이면 그 사실을 즉시 알린다. 타이핑 이벤트는 재전송하지 않으므로
        // (휘발성이라 의미가 없다), 연결 직후 한 번은 현재 상태를 스냅샷으로 줘야 한다.
        // 이게 없으면 상담사가 계속 타이핑 중인데도 방금 붙은 사용자 화면에는 아무 표시가 없다.
        if (typingStore.isTyping(conversationId)) {
            try {
                emitter.send(SseEmitter.event()
                        .name(ChatStreamEvent.Type.TYPING.name())
                        .data(new ChatStreamEvent.Typing(true)));
            } catch (IOException | IllegalStateException e) {
                // 연결이 이미 끊겼다. 초기 스냅샷 실패로 연결 자체를 실패시킬 이유는 없다.
                log.debug("타이핑 초기 상태 전송 실패. conversationId={}", conversationId);
            }
        }

        return Optional.of(emitter);
    }

    /**
     * 상담사 타이핑 하트비트.
     *
     * <p>{@code typing=false}로 오면 즉시 지운다. TTL 만료를 기다리면 상담사가 전송을 끝낸 뒤에도
     * 최대 6초 동안 "입력 중"이 남아, 답변이 이미 도착한 화면에서 계속 점이 깜빡인다.
     */
    public void updateTyping(Long conversationId, Long adminId, boolean typing) {
        if (typing) {
            typingStore.markTyping(conversationId, adminId);
        } else {
            typingStore.clearTyping(conversationId);
        }
        eventPublisher.publish(ChatStreamEvent.typing(conversationId, typing));
    }
}
