package com.ms.petopia.api.chat.dto;

import com.ms.petopia.api.chat.entity.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 메시지 한 건. {@code messageId}는 화면 표시용이자 <b>다음 조회의 커서</b>다.
 *
 * <p>보낸 사람의 user_id는 내보내지 않는다. 상담사가 누구인지는 사용자가 알 필요가 없고,
 * 노출하면 개인정보가 된다.
 */
public record ChatMessageResponse(
        Long messageId,
        // 여러 상담 이력을 한 창에 이어 보여줄 때, 어디서 대화가 바뀌는지 화면이 알아야
        // "이전 상담" 구분선을 그릴 수 있다. 없으면 지난 답변이 방금 한 문의의 답처럼 읽힌다.
        Long conversationId,
        ChatSenderType senderType,
        String content,
        LocalDateTime createdAt
) {
    public static ChatMessageResponse from(ChatMessage message) {
        return new ChatMessageResponse(
                message.getMessageId(),
                message.getConversationId(),
                message.getSenderType(),
                message.getContent(),
                message.getCreatedAt());
    }

    public static List<ChatMessageResponse> fromAll(List<ChatMessage> messages) {
        return messages.stream().map(ChatMessageResponse::from).toList();
    }
}
