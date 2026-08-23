package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.AdminCancelReservationRequest;
import com.ms.petopia.api.reservation.dto.CancelReservationResponse;
import com.ms.petopia.api.reservation.service.ReservationCancellationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자가 관람객 예약에 직접 손대는 API.
 *
 * <p>예약자 <b>목록 조회</b>는 여기가 아니라 {@code GET /api/v1/fairs/{fairId}/reservations}
 * ({@link ReservationController})에 있다 — 관람객용 예약 생성과 경로가 겹쳐 메서드로만 갈리는
 * 자리라 SecurityConfig에서 GET만 따로 관리자 권한으로 묶어뒀다. 반면 이 컨트롤러는
 * {@code /api/v1/admin/**} 아래라 SecurityConfig가 EVENT_ADMIN·SUPER_ADMIN만 들여보낸다.
 *
 * <p>role만으로는 "그 행사 담당자인지"까지 못 가리므로, 서비스 계층에서
 * {@code FairAdminAccessGuard}가 한 번 더 확인한다(SUPER_ADMIN은 항상 통과).
 */
@RestController
@RequestMapping("/api/v1/admin/fairs/{fairId}/reservations")
@RequiredArgsConstructor
public class ReservationAdminController {

    private final ReservationCancellationService cancellationService;

    /**
     * 관람객 대신 예약을 취소한다(대행 취소). 취소 마감이 지났어도, 현장예매 건이어도 처리된다.
     * 유료 확정 예약이면 전액 환불이 함께 나간다.
     *
     * <p>실패 코드: A002 담당 행사 아님 / R010 예약 없음(경로의 행사 소속이 아닌 경우 포함) /
     * R013 취소 불가 상태(이미 입장·취소·만료) / R020 환불할 결제 없음 / R021 결제 진행 중.
     */
    @PatchMapping("/{reservationId}/cancel")
    public CancelReservationResponse cancelByAdmin(
            @PathVariable Long fairId,
            @PathVariable Long reservationId,
            @AuthenticationPrincipal Long adminUserId,
            @RequestBody AdminCancelReservationRequest request
    ) {
        return cancellationService.cancelByAdmin(fairId, reservationId, adminUserId, request);
    }
}
