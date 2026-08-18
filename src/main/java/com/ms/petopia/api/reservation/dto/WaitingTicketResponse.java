package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;

/**
 * 대기열 티켓의 현재 상태.
 *
 * @param token                 대기 토큰. 보호 대상 API 호출 시 {@code X-Waiting-Token} 헤더로 보낸다.
 * @param status                {@code WAITING} 대기 중 / {@code ADMITTED} 통과 / {@code BYPASSED}
 *                              대기열 미적용(행사가 대상이 아니거나 Redis 장애)
 * @param position              내 대기 순번(1부터). 통과·미적용이면 0.
 * @param ahead                 내 앞에 남은 인원. 통과·미적용이면 0.
 * @param estimatedWaitSeconds  예상 대기 시간(초). 최근 승급 속도로 추정한 값이라 정확하지 않다.
 *                              승급 이력이 없어 추정할 수 없으면 null.
 * @param expiresAt             통과한 슬롯의 만료 시각. 대기 중이면 null.
 */
public record WaitingTicketResponse(
        String token,
        String status,
        long position,
        long ahead,
        Long estimatedWaitSeconds,
        LocalDateTime expiresAt
) {

    public static final String WAITING = "WAITING";
    public static final String ADMITTED = "ADMITTED";
    public static final String BYPASSED = "BYPASSED";

    /** 대기열을 적용하지 않는 경우. 프론트는 이 응답을 받으면 곧장 예약 화면으로 간다. */
    public static WaitingTicketResponse bypassed() {
        return new WaitingTicketResponse(null, BYPASSED, 0, 0, null, null);
    }
}
