package com.ms.petopia.api.chat.dto;

import com.ms.petopia.api.chat.entity.ChatMenu;

/**
 * 위젯에 그릴 버튼 하나.
 *
 * <p>{@code fixedAnswer}와 {@code aiContext}는 일부러 내보내지 않는다. 고정 답변은 버튼을
 * 눌러 대화가 만들어질 때 메시지로 내려가고, aiContext는 프롬프트 재료라 사용자에게
 * 노출할 값이 아니다.
 */
public record ChatMenuResponse(
        Long menuId,
        String code,
        String label,
        ChatAnswerType answerType
) {
    public static ChatMenuResponse from(ChatMenu menu) {
        return new ChatMenuResponse(menu.getMenuId(), menu.getCode(), menu.getLabel(), menu.getAnswerType());
    }
}
