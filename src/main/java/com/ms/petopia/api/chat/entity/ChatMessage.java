package com.ms.petopia.api.chat.entity;

import com.ms.petopia.api.chat.dto.ChatSenderType;
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
public class ChatMessage {
    private Long messageId;
    private Long conversationId;
    private ChatSenderType senderType;
    private Long senderId;
    private Long menuId;
    /** 상담 본문. 개인정보가 담기는 자리라 로그로 새지 않게 toString에서 제외한다. */
    @ToString.Exclude
    private String content;
    private LocalDateTime createdAt;
}
