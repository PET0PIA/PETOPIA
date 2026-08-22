package com.ms.petopia.api.chat.dto;

/** 입력이 잠긴 이유. 잠기지 않았으면 null로 내려간다. */
public enum ChatLockReason {

    /**
     * 종료된 대화. 풀리지 않는다.
     *
     * <p>상수가 하나뿐이어도 enum을 없애지 않는다. 잠금 사유는 프론트가 문구를 고르는
     * 축이고, 나중에 사유가 하나 더 생길 때 {@code boolean}에서 되돌리는 비용이
     * 지금 이 파일을 남겨두는 비용보다 크다.
     */
    CLOSED;

    /** 상태 → 잠금 사유. 잠기지 않는 상태면 null. 이 판정이 두 군데로 갈라지지 않도록 여기 둔다. */
    public static ChatLockReason of(ChatConversationStatus status) {
        return switch (status) {
            case CLOSED -> CLOSED;
            case BOT, WAITING_AGENT, AI_HANDLED, IN_PROGRESS -> null;
        };
    }
}
