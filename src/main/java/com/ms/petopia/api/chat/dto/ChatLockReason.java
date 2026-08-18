package com.ms.petopia.api.chat.dto;

/** 입력이 잠긴 이유. 잠기지 않았으면 null로 내려간다. */
public enum ChatLockReason {
    /** AI가 1회 답했고 상담사 차례. 상담사가 답하면 풀린다. */
    AI_ANSWERED,
    /** 종료된 대화. 풀리지 않는다. */
    CLOSED;

    /** 상태 → 잠금 사유. 잠기지 않는 상태면 null. 이 판정이 두 군데로 갈라지지 않도록 여기 둔다. */
    public static ChatLockReason of(ChatConversationStatus status) {
        return switch (status) {
            case AI_ANSWERED -> AI_ANSWERED;
            case CLOSED -> CLOSED;
            case BOT, WAITING_AGENT, IN_PROGRESS -> null;
        };
    }
}
