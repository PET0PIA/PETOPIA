package com.ms.petopia.api.chat.dto;

/**
 * 문의 유형 버튼을 눌렀을 때 무엇이 답하는지.
 *
 * <p>{@code FIXED}는 DB에 저장된 답변을 그대로 내보내므로 상담사도 AI도 개입하지 않는다.
 * {@code AI}와 {@code AGENT}는 둘 다 사용자의 질문을 받아 상담사 대기열로 들어가고,
 * 운영시간 밖일 때 AI가 1회 먼저 답하느냐만 다르다.
 */
public enum ChatAnswerType {
    FIXED,
    AI,
    AGENT
}
