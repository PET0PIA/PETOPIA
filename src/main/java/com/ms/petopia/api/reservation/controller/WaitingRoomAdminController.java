package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.UpdateWaitingRoomPolicyRequest;
import com.ms.petopia.api.reservation.dto.WaitingRoomPolicyResponse;
import com.ms.petopia.api.reservation.service.WaitingRoomPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사별 대기열 설정. 오픈 직후 백엔드 지표를 보며 통과 인원을 조정하는 용도다.
 *
 * <p>경로가 {@code /api/v1/admin/**} 아래라 SecurityConfig가 EVENT_ADMIN·SUPER_ADMIN만
 * 들여보낸다. 다만 role만으로는 "그 행사 담당자인지"까지 가릴 수 없어서,
 * {@code ReservationOperatorAccessService}가 서비스 계층에서 한 번 더 확인한다.
 */
@RestController
@RequestMapping("/api/v1/admin/fairs/{fairId}/waiting-room-policy")
@RequiredArgsConstructor
public class WaitingRoomAdminController {

    private final WaitingRoomPolicyService waitingRoomPolicyService;

    @GetMapping
    public WaitingRoomPolicyResponse getPolicy(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long actorUserId
    ) {
        return waitingRoomPolicyService.get(fairId, actorUserId);
    }

    /**
     * 대기열을 켜고 끄거나 통과 인원을 바꾼다.
     *
     * <p>조회 때 받은 {@code version}을 {@code expectedVersion}으로 그대로 실어 보내야 한다.
     * 오픈 중에 두 관리자가 각자 값을 조정하면 나중에 쓴 쪽이 상대 변경을 모르고 덮어쓰는데,
     * 이 값이 그걸 <code>409 R023</code>으로 잡는다.
     */
    @PutMapping
    public WaitingRoomPolicyResponse savePolicy(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long actorUserId,
            @RequestBody UpdateWaitingRoomPolicyRequest request
    ) {
        return waitingRoomPolicyService.save(fairId, actorUserId, request);
    }
}
