package com.ms.petopia.api.chat.dto;

import com.ms.petopia.api.chat.entity.ChatConversation;
import com.ms.petopia.api.chat.entity.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 상담사가 보는 대화 상세.
 *
 * <p>고객용 응답과 달리 {@code inputLocked}가 없다. 잠금은 <b>고객 입력에만</b> 걸리는 규칙이고,
 * 상담사는 어느 상태에서든 답변할 수 있어야 한다 - 오히려 잠긴 대화(WAITING_AGENT)가 상담사가
 * 답해야 할 대화다. 종료된 대화만 예외이며 그건 서버가 전송 시점에 거부한다.
 */
public record AdminChatConversationDetailResponse(
        Long conversationId,
        ChatConversationStatus status,
        String menuLabel,
        String requesterType,
        Long assignedAdminId,
        LocalDateTime createdAt,
        List<ChatMessageResponse> messages
) {

    public static AdminChatConversationDetailResponse of(ChatConversation conversation,
                                                         String menuLabel,
                                                         List<ChatMessage> messages) {
        return new AdminChatConversationDetailResponse(
                conversation.getConversationId(),
                conversation.getStatus(),
                menuLabel,
                conversation.getUserId() != null ? "MEMBER" : "GUEST",
                conversation.getAssignedAdminId(),
                conversation.getCreatedAt(),
                ChatMessageResponse.fromAll(messages));
    }
}
