package com.ms.petopia.api.chat.dto;

import com.ms.petopia.api.chat.entity.ChatConversation;
import com.ms.petopia.api.chat.entity.ChatMessage;

import java.util.List;

/**
 * 대화 한 건의 현재 모습.
 *
 * <p>{@code inputLocked}를 서버가 계산해서 내려주는 것이 핵심이다. 프론트가 상태 이름을 보고
 * 스스로 판단하게 두면, 규칙이 바뀔 때마다 서버와 프론트 두 곳을 고쳐야 하고 둘이 어긋나는
 * 순간 사용자는 "보낼 수 있어 보이는데 거부당하는" 화면을 만난다.
 *
 * @param guestKey 대화를 새로 만들 때만 채워진다(그때 발급하므로). 이후 조회에서는 null -
 *                 클라이언트가 이미 저장하고 있고, 매번 실어 보내면 노출 지점만 늘어난다.
 */
public record ChatConversationResponse(
        Long conversationId,
        String guestKey,
        ChatConversationStatus status,
        boolean inputLocked,
        ChatLockReason lockReason,
        List<ChatMessageResponse> messages
) {

    public static ChatConversationResponse of(ChatConversation conversation,
                                              List<ChatMessage> messages,
                                              String issuedGuestKey) {
        ChatConversationStatus status = conversation.getStatus();
        return new ChatConversationResponse(
                conversation.getConversationId(),
                issuedGuestKey,
                status,
                !status.acceptsUserMessage(),
                ChatLockReason.of(status),
                ChatMessageResponse.fromAll(messages));
    }
}
