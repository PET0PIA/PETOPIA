package com.ms.petopia.api.chat.dto;

/**
 * 문의 유형 버튼을 눌렀을 때 무엇이 답하는지.
 *
 * <p>{@code FIXED}는 저장된 답변을 위젯이 즉시 렌더한다 - 상담 세션도 메시지도 만들지 않고,
 * 클릭만 집계 테이블에 남는다. {@code AGENT}는 상담 세션을 만들어 상담사 대기열로 보낸다.
 *
 * <p><b>{@code AI}는 없다.</b> AI는 버튼 유형이 아니라 상담원 연결 세션이 운영시간 밖일 때의
 * 대체 응대다. 상수를 지워두면 {@code {"answerType":"AI"}} 요청이 역직렬화 단계에서 이미
 * 거부되므로, 서버가 이 값을 받아들이는 경로가 남지 않는다.
 */
public enum ChatAnswerType {
    FIXED,
    AGENT
}
