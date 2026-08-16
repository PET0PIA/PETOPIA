package com.ms.petopia.api.chat.sse;

/**
 * 인스턴스 사이를 오가는 상담 이벤트.
 *
 * <p>payload를 통째로 실어 보낸다. ID만 보내고 각 인스턴스가 DB를 다시 읽게 하면, 이벤트
 * 하나당 조회가 연결 수만큼 늘고 커밋 직후의 복제 지연에도 노출된다.
 *
 * @param type           {@link Type}의 이름. SSE 이벤트 이름으로도 그대로 쓴다.
 * @param conversationId 어느 대화의 이벤트인지
 * @param eventId        SSE {@code id:} 필드로 나갈 값. 메시지면 message_id, 아니면 null
 * @param payload        JSON으로 직렬화될 본문
 */
public record ChatStreamEvent(
        String type,
        Long conversationId,
        Long eventId,
        Object payload
) {

    public enum Type {
        /** 새 메시지 한 건. */
        MESSAGE,
        /** 상담사 입력 중 여부. 저장하지 않는 휘발성 신호다. */
        TYPING,
        /** 대화 상태(잠금 포함)가 바뀌었다. */
        STATUS
    }

    public static ChatStreamEvent message(Long conversationId, Long messageId, Object payload) {
        return new ChatStreamEvent(Type.MESSAGE.name(), conversationId, messageId, payload);
    }

    public static ChatStreamEvent typing(Long conversationId, boolean typing) {
        return new ChatStreamEvent(Type.TYPING.name(), conversationId, null, new Typing(typing));
    }

    public static ChatStreamEvent status(Long conversationId, Object payload) {
        return new ChatStreamEvent(Type.STATUS.name(), conversationId, null, payload);
    }

    /** 상담사가 누구인지는 싣지 않는다. 화면에는 "상담사"로만 표시한다. */
    public record Typing(boolean typing) {
    }
}
