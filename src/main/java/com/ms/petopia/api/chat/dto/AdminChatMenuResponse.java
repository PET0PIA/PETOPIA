package com.ms.petopia.api.chat.dto;

import com.ms.petopia.api.chat.entity.ChatMenu;

import java.util.List;

/**
 * 관리자용 버튼 정보.
 *
 * <p>고객용 {@link ChatMenuResponse}와 달리 {@code fixedAnswer}/{@code aiContext}까지 준다 -
 * 운영자가 편집해야 하는 값이다.
 */
public record AdminChatMenuResponse(
        Long menuId,
        String code,
        String label,
        ChatAnswerType answerType,
        String fixedAnswer,
        String aiContext,
        int displayOrder,
        boolean isActive
) {

    public static AdminChatMenuResponse from(ChatMenu menu) {
        return new AdminChatMenuResponse(
                menu.getMenuId(),
                menu.getCode(),
                menu.getLabel(),
                menu.getAnswerType(),
                menu.getFixedAnswer(),
                menu.getAiContext(),
                menu.getDisplayOrder() != null ? menu.getDisplayOrder() : 0,
                Boolean.TRUE.equals(menu.getIsActive()));
    }

    public static List<AdminChatMenuResponse> fromAll(List<ChatMenu> menus) {
        return menus.stream().map(AdminChatMenuResponse::from).toList();
    }
}
