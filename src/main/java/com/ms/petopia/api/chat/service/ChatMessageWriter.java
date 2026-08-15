package com.ms.petopia.api.chat.service;

import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import com.ms.petopia.api.chat.dto.ChatLockReason;
import com.ms.petopia.api.chat.dto.ChatMessageResponse;
import com.ms.petopia.api.chat.dto.ChatSenderType;
import com.ms.petopia.api.chat.entity.ChatMessage;
import com.ms.petopia.api.chat.mapper.ChatMessageMapper;
import com.ms.petopia.api.chat.sse.ChatEventPublisher;
import com.ms.petopia.api.chat.sse.ChatStreamEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 메시지 저장과 실시간 발행을 한 쌍으로 묶는다.
 *
 * <p>고객 쪽({@link ChatConversationService})과 상담사 쪽({@link AdminChatService})이 모두
 * 메시지를 남긴다. 각자 저장하고 각자 발행하게 두면 한쪽에만 발행을 빠뜨리는 실수가 나는데,
 * 그 증상은 "메시지는 저장됐지만 상대 화면에 실시간으로 안 뜬다"라 새로고침하면 멀쩡해 보여
 * 발견이 늦다. 저장과 발행을 분리할 수 없게 만들어 그 실수 자체를 없앤다.
 */
@Component
@RequiredArgsConstructor
public class ChatMessageWriter {

    private final ChatMessageMapper messageMapper;
    private final ChatEventPublisher eventPublisher;
    private final ChatTimeProvider timeProvider;

    public ChatMessage append(Long conversationId, ChatSenderType senderType,
                              Long senderId, Long menuId, String content) {
        ChatMessage message = ChatMessage.builder()
                .conversationId(conversationId)
                .senderType(senderType)
                .senderId(senderId)
                .menuId(menuId)
                .content(content)
                .build();
        messageMapper.insert(message);

        // createdAt은 DB의 NOW()로 채워지므로 방금 만든 객체에는 없다. 화면이 시각을 표시하려면
        // 값이 있어야 해서 애플리케이션 시각을 채운다 - 초 단위 오차는 있지만, 이걸 얻자고
        // 방금 넣은 행을 다시 SELECT하는 건 메시지마다 왕복을 하나 더 만드는 일이다.
        message.setCreatedAt(timeProvider.now());
        eventPublisher.publish(ChatStreamEvent.message(
                conversationId, message.getMessageId(), ChatMessageResponse.from(message)));
        return message;
    }

    /** 잠금 해제·종료처럼 사용자 화면의 입력 가능 여부가 달라지는 변화를 알린다. */
    public void publishStatus(Long conversationId, ChatConversationStatus status) {
        eventPublisher.publish(ChatStreamEvent.status(conversationId,
                new ChatStatusPayload(status, !status.acceptsUserMessage(), ChatLockReason.of(status))));
    }

    /** 상태 이벤트 본문. 필드 이름을 응답 DTO와 맞춰 프론트가 같은 코드로 반영한다. */
    public record ChatStatusPayload(ChatConversationStatus status,
                                    boolean inputLocked,
                                    ChatLockReason lockReason) {
    }
}
