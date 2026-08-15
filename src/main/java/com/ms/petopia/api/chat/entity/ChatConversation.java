package com.ms.petopia.api.chat.entity;

import com.ms.petopia.api.chat.dto.ChatConversationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ChatConversation {
    private Long conversationId;
    private Long userId;
    private String guestKey;
    private Long menuId;
    private Long fairId;
    private ChatConversationStatus status;
    private Integer aiAnswerCount;
    private Long assignedAdminId;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private LocalDateTime closedAt;

    /**
     * 이 대화를 요청자가 열람·전송할 수 있는지.
     *
     * <p>로그인 사용자는 user_id로, 게스트는 guest_key로 판정한다. 게스트 키는 서버가 발급한
     * UUIDv4지만 그래도 <b>대화에 저장된 값과 일치하는지 반드시 확인</b>해야 한다 - 확인하지
     * 않으면 conversationId만 바꿔가며 남의 상담 내용을 읽을 수 있다.
     */
    public boolean isOwnedBy(Long requesterUserId, String requesterGuestKey) {
        if (requesterUserId != null && requesterUserId.equals(userId)) {
            return true;
        }
        return guestKey != null && guestKey.equals(requesterGuestKey);
    }
}
