package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.WaitingTicketResponse;
import com.ms.petopia.api.reservation.service.WaitingRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 오픈 직후 예약 유입을 조절하는 대기열 API.
 *
 * <p>이 API 자체는 대기열 게이트({@code WaitingRoomInterceptor})의 보호 대상이 아니다 —
 * 대기 토큰을 받으러 오는 요청까지 토큰을 요구하면 아무도 줄을 설 수 없다.
 */
@RestController
@RequestMapping("/api/v1/fairs/{fairId}/waiting-room")
@RequiredArgsConstructor
public class WaitingRoomController {

    private final WaitingRoomService waitingRoomService;

    /** 대기 토큰을 발급한다. 빈 슬롯이 있으면 발급과 동시에 ADMITTED로 돌아온다. */
    @PostMapping("/tickets")
    public ResponseEntity<WaitingTicketResponse> issueTicket(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long userId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(waitingRoomService.issue(fairId, userId));
    }

    /**
     * 순번을 조회한다. 승급 처리도 이 호출에 얹혀 일어나므로, 아무도 폴링하지 않으면
     * 승급도 일어나지 않는다(승급이 필요한 사람이 없다는 뜻이라 문제가 되지 않는다).
     */
    @GetMapping("/tickets/{token}")
    public WaitingTicketResponse getTicket(
            @PathVariable Long fairId,
            @PathVariable String token,
            @AuthenticationPrincipal Long userId
    ) {
        return waitingRoomService.status(fairId, token, userId);
    }

    /**
     * 대기를 포기한다. 슬롯을 즉시 반납해 뒷사람이 TTL을 기다리지 않아도 되게 한다.
     *
     * <p>토큰만으로 지우지 않는다 — 인증 주체가 그 토큰의 주인일 때만 반납한다.
     */
    @DeleteMapping("/tickets/{token}")
    public ResponseEntity<Void> leave(
            @PathVariable Long fairId,
            @PathVariable String token,
            @AuthenticationPrincipal Long userId
    ) {
        waitingRoomService.leave(fairId, token, userId);
        return ResponseEntity.noContent().build();
    }
}
