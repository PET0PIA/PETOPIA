package com.ms.petopia.api.chat.dto;

import com.ms.petopia.api.chat.entity.ChatMenu;

/**
 * 위젯에 그릴 버튼 하나.
 *
 * <p>{@code fixedAnswer}를 함께 내린다. 고정형은 더 이상 대화를 만들지 않으므로 답변이
 * 메시지로 내려올 경로가 없고, 클릭 시 따로 조회하면 "누르면 바로 답변"이 한 번의 왕복만큼
 * 늦어진다. 공개 안내문이라 미리 내려도 노출 위험이 없다.
 *
 * <p>{@code AGENT} 유형은 이 값이 null이다 - 저장된 답변이 아니라 상담사가 답한다.
 */
public record ChatMenuResponse(
        Long menuId,
        String code,
        String label,
        ChatAnswerType answerType,
        String fixedAnswer
) {
    public static ChatMenuResponse from(ChatMenu menu) {
        return new ChatMenuResponse(menu.getMenuId(), menu.getCode(), menu.getLabel(),
                menu.getAnswerType(), menu.getFixedAnswer());
    }
}
