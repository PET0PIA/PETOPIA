package com.ms.petopia.api.reservation.dto;

/**
 * 행사별 대기열 정책 변경 요청.
 *
 * @param expectedVersion 조회 때 받은 {@code version}. 정책 행이 아직 없으면 0을 보낸다.
 *                        생략하면 충돌로 거절한다 — 오픈 중에 두 관리자가 통과 인원을
 *                        각자 조정하면 나중에 쓴 쪽이 상대 변경을 모르고 덮어쓴다.
 */
public record UpdateWaitingRoomPolicyRequest(
        Boolean enabled,
        Integer activeLimit,
        Integer expectedVersion
) {
}
