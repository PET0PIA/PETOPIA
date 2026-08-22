package com.ms.petopia.api.chat.dto;

/**
 * 대화 상태. 사용자가 지금 메시지를 보낼 수 있는지를 이 값 하나로 판정한다.
 *
 * <pre>
 *   (상담원 연결 클릭)
 *         │
 *         ▼
 *       BOT ──(질문)──▶ WAITING_AGENT ──(상담사 답변)──▶ IN_PROGRESS
 *                           │                                │
 *                 (운영시간 외: AI 응답)               (재질문) │
 *                           ▼                                ▼
 *                      AI_HANDLED ──(재질문)──▶ WAITING_AGENT / AI_HANDLED
 *
 *   어느 상태에서든 종료하면 CLOSED
 * </pre>
 *
 * <p><b>잠기는 상태는 {@code CLOSED} 하나뿐이다.</b> 답변을 기다린다는 이유만으로는 막지
 * 않는다 - 사용자가 설명을 덧붙이거나 오타를 정정할 길을 막을 이유가 없다.
 *
 * <p><b>이 enum만 고쳐서는 판정이 바뀌지 않는다.</b> 실제 차단은
 * {@code ChatConversationMapper.markWaitingAgent}의 조건부 UPDATE가 하고, 여기 열거된 값과
 * 그 SQL의 허용 목록은 손으로 맞춰야 한다. 한쪽만 고치면 컴파일은 통과하고 런타임에서
 * 조용히 막힌다.
 */
public enum ChatConversationStatus {

    /** 상담원 연결로 대화가 만들어졌고 아직 질문이 없는 상태. */
    BOT,

    /**
     * 상담사 답변을 기다리는 중. 입력은 <b>잠기지 않는다</b> - 사용자는 말을 더 보탤 수 있다.
     *
     * <p>대기열({@code OPEN})에 올라오는 상태는 이것과 {@link #IN_PROGRESS}다.
     */
    WAITING_AGENT,

    /**
     * 운영시간 밖에 AI가 응대해 마무리된 상태. 입력은 열려 있다.
     *
     * <p><b>대기열에서 빠진다.</b> 상담사가 답해야 할 건이 아니기 때문이다 - 사용자가 다시
     * 물으면 그때 {@code WAITING_AGENT}로 돌아간다(또 운영시간 밖이면 AI가 다시 답하고
     * 이 상태로 남는다).
     *
     * <p>{@link #BOT}을 재사용하지 않는 이유: 그러면 "연결됐지만 질문 없음"과 "AI가 응대함"이
     * 한 값에 섞이고, 상담사가 "AI가 밤에 뭐라고 답했는지" 확인할 필터 축이 사라진다.
     */
    AI_HANDLED,

    /** 상담사가 한 번 이상 답한 상태. 사용자가 이어서 질문할 수 있다. */
    IN_PROGRESS,

    /** 종료된 대화. 더 이상 메시지를 받지 않는다. 유일한 입력 잠금 상태다. */
    CLOSED;

    /** 이 상태에서 사용자가 새 메시지를 보낼 수 있는지. */
    public boolean acceptsUserMessage() {
        return this != CLOSED;
    }

    /** 상담사가 답해야 하는 대화인지(대기열 노출 대상). */
    public boolean needsAgentReply() {
        return this == WAITING_AGENT;
    }
}
