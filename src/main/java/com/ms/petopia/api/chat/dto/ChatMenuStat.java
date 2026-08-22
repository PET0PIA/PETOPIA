package com.ms.petopia.api.chat.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 문의 유형별 운영 지표(최근 30일).
 *
 * <p>고정형 유형은 세션을 만들지 않으므로 {@code conversationCount}가 0이고, 수요는
 * {@code clickCount}에만 나타난다. 두 값을 함께 봐야 "어떤 문의가 많은가"가 보인다.
 */
@Getter
@Setter
@ToString
public class ChatMenuStat {
    private String menuLabel;

    /** 이 유형으로 만들어진 상담 수. 고정형은 세션을 만들지 않으므로 항상 0이다. */
    private long conversationCount;

    /** 이 유형 버튼이 눌린 횟수. 고정형의 유일한 수요 지표다. */
    private long clickCount;

    /** 상담사 답변을 기다리는 대화 수. 자동 응대로 끝난 건(AI_HANDLED)은 세지 않는다. */
    private long waitingCount;

    /**
     * 첫 상담사 답변까지 걸린 평균 시간(초). 아직 아무도 답하지 않은 유형이면 null이다 -
     * 0으로 채우면 "즉시 답한다"로 읽혀 정반대의 인상을 준다.
     */
    private Long avgFirstResponseSeconds;

    /** AI가 한 번 이상 응대한 대화 수. */
    private long aiAnsweredCount;
}
