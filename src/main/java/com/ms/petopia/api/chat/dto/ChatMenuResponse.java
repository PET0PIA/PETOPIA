package com.ms.petopia.api.chat.dto;

import com.ms.petopia.api.chat.entity.ChatMenu;

/**
 * 위젯에 그릴 버튼 하나.
 *
 * <p>{@code fixedAnswer}를 함께 내린다. 고정형은 더 이상 대화를 만들지 않으므로 답변이
 * 메시지로 내려올 경로가 없고, 클릭 시 따로 조회하면 "누르면 바로 답변"이 한 번의 왕복만큼
 * 늦어진다. 공개 안내문이라 미리 내려도 노출 위험이 없다.
 *
 * <p>{@code AGENT} 유형에는 {@code null}을 넣는다. 컬럼에는 값이 남아 있을 수 있다 -
 * 운영자가 {@code FIXED}였던 버튼을 {@code AGENT}로 바꾸면 예전 답변이 그대로 있다. 그걸
 * 그대로 내리면 화면이 쓰지도 않는 본문이 공개 응답에 실린다.
 */
public record ChatMenuResponse(
        Long menuId,
        String code,
        String label,
        ChatAnswerType answerType,
        String fixedAnswer
) {
    public static ChatMenuResponse from(ChatMenu menu) {
        boolean fixed = menu.getAnswerType() == ChatAnswerType.FIXED;
        return new ChatMenuResponse(menu.getMenuId(), menu.getCode(), menu.getLabel(),
                menu.getAnswerType(), fixed ? menu.getFixedAnswer() : null);
    }
}
