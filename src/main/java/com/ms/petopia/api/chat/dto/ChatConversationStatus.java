package com.ms.petopia.api.chat.dto;

/**
 * 대화 상태. 사용자가 지금 메시지를 보낼 수 있는지를 이 값 하나로 판정한다.
 *
 * <pre>
 *   BOT ──(질문)──▶ WAITING_AGENT ──(AI 1회 답변)──▶ AI_ANSWERED
 *                     │  ▲                              │
 *          (계속 질문 가능)                        (입력 잠김)
 *                     │  │                              │
 *                     │  └────(재질문)─── IN_PROGRESS ◀──┘
 *                     └──────(상담사 답변)───┘   (상담사 답변으로 해제)
 *
 *   어느 상태에서든 종료하면 CLOSED
 * </pre>
 *
 * <p><b>잠기는 상태는 {@code AI_ANSWERED} 하나뿐이다.</b> 답변을 기다린다는 이유만으로는
 * 막지 않는다 - 사용자가 설명을 덧붙이거나 오타를 정정할 길을 막을 이유가 없다.
 */
public enum ChatConversationStatus {

    /** 고정 답변만 오간 상태. 사용자가 자유롭게 질문할 수 있다. */
    BOT,

    /**
     * 상담사 답변을 기다리는 중. 입력은 <b>잠기지 않는다</b> - 사용자는 말을 더 보탤 수 있다.
     *
     * <p>대기열에는 이 상태와 {@link #AI_ANSWERED}가 함께 올라온다.
     */
    WAITING_AGENT,

    /**
     * AI가 1회 답했고 상담사 차례를 기다리는 중. <b>사용자 입력이 잠긴다.</b>
     *
     * <p>"AI 답변 후에는 사람 상담사가 답하기 전까지 더 질문할 수 없다"는 요구가
     * 이 상태로 표현된다. AI는 대화당 한 번만 답하므로, 여기서 질문을 더 받아봐야
     * 답할 주체가 없다.
     */
    AI_ANSWERED,

    /** 상담사가 한 번 이상 답한 상태. 사용자가 이어서 질문할 수 있다. */
    IN_PROGRESS,

    /** 종료된 대화. 더 이상 메시지를 받지 않는다. */
    CLOSED;

    /** 이 상태에서 사용자가 새 메시지를 보낼 수 있는지. */
    public boolean acceptsUserMessage() {
        return this == BOT || this == WAITING_AGENT || this == IN_PROGRESS;
    }

    /** 상담사가 답해야 하는 대화인지(대기열 노출 대상). */
    public boolean needsAgentReply() {
        return this == WAITING_AGENT || this == AI_ANSWERED;
    }
}
