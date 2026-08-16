package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;

/**
 * 관리자 화면에 표시할 행사별 대기열 정책.
 *
 * @param version   낙관적 잠금용. 수정 요청에 이 값을 그대로 담아 보내면 그 사이 다른
 *                  관리자가 바꿨을 때 충돌로 거절된다. 정책 행이 아직 없으면 0.
 * @param updatedAt 마지막 변경 시각. 정책 행이 없으면 null
 */
public record WaitingRoomPolicyResponse(
        Long fairId,
        boolean enabled,
        int activeLimit,
        int version,
        LocalDateTime updatedAt
) {
}
