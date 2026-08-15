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
    private String content;
    private LocalDateTime createdAt;
}
