package com.ms.petopia.api.chat.entity;

import com.ms.petopia.api.chat.dto.ChatAnswerType;
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
public class ChatMenu {
    private Long menuId;
    private Long parentMenuId;
    private String code;
    private String label;
    private Integer displayOrder;
    private ChatAnswerType answerType;
    private String fixedAnswer;
    private String aiContext;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
