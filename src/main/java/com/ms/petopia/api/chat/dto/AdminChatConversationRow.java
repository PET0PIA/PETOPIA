package com.ms.petopia.api.chat.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * 대기열 목록 한 줄(매퍼 조회 결과).
 *
 * <p>마지막 메시지 미리보기를 함께 가져온다. 목록에서 "무엇을 물었는지"가 안 보이면 상담사가
 * 매번 대화를 열어봐야 우선순위를 정할 수 있다.
 */
@Getter
@Setter
@ToString
public class AdminChatConversationRow {
    private Long conversationId;
    private ChatConversationStatus status;
    private String menuLabel;
    private Long userId;
    private Long assignedAdminId;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    /** 상담 본문 일부. {@link ChatMessage#getContent()}와 같은 이유로 toString에서 제외한다. */
    @ToString.Exclude
    private String lastMessageContent;
    private ChatSenderType lastMessageSenderType;
}
