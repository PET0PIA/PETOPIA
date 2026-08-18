package com.ms.petopia.api.chat.dto;

/**
 * 메시지를 누가 보냈는지.
 *
 * <p>{@code BOT}(고정 답변)과 {@code AI}(Claude 생성)를 나누는 이유는 화면 표시 때문이다.
 * AI 답변에는 "자동 답변" 배지를 붙여 사람이 쓴 답변과 구분해야 한다 - 오정보가 섞였을 때
 * 사용자가 무엇을 신뢰할지 판단할 근거가 된다.
 * {@code SYSTEM}은 접수 안내나 잠금 안내처럼 서버가 넣는 상태 메시지다.
 */
public enum ChatSenderType {
    USER,
    BOT,
    AI,
    AGENT,
    SYSTEM
}
