package com.ms.petopia.api.chat.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * 문의 유형별 운영 지표(최근 30일).
 *
 * @param avgFirstResponseSeconds 첫 상담사 답변까지 걸린 평균 시간. 아직 아무도 답하지 않은
 *                                유형이면 null이다 - 0으로 채우면 "즉시 답한다"로 읽혀
 *                                정반대의 인상을 준다.
 */
@Getter
@Setter
@ToString
public class ChatMenuStat {
    private String menuLabel;
    private long conversationCount;
    private long waitingCount;
    private Long avgFirstResponseSeconds;
    private long aiAnsweredCount;
}
