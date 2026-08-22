package com.ms.petopia.api.chat.dto;

import com.ms.petopia.api.chat.entity.ChatMenu;

import java.util.List;

/**
 * 관리자용 버튼 정보.
 *
 * <p>비활성 버튼까지 포함해 내려준다 - 운영자가 내렸던 버튼을 다시 올릴 수 있어야 한다.
 * 고객용 {@link ChatMenuResponse}는 활성 버튼만 받는다.
 */
public record AdminChatMenuResponse(
        Long menuId,
        String code,
        String label,
        ChatAnswerType answerType,
        String fixedAnswer,
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
                menu.getDisplayOrder() != null ? menu.getDisplayOrder() : 0,
                Boolean.TRUE.equals(menu.getIsActive()));
    }

    public static List<AdminChatMenuResponse> fromAll(List<ChatMenu> menus) {
        return menus.stream().map(AdminChatMenuResponse::from).toList();
    }
}
