package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.CreateReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.service.ReservationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    @PostMapping("/fairs/{fairId}/reservations")
    public ResponseEntity<CreateReservationResponse> createAdvanceReservation(
            @PathVariable Long fairId,
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long userId,
            @RequestBody CreateReservationRequest request
    ) {
        // TODO 인증 도메인 완성 후 X-User-Id 대신 인증 Principal에서 userId를 가져온다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(reservationService.create(fairId, userId, request));
    }
}
