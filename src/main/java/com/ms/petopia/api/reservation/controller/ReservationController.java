package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.CreateOnsiteReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateOnsiteReservationResponse;
import com.ms.petopia.api.reservation.dto.CreateReservationRequest;
import com.ms.petopia.api.reservation.dto.CreateReservationResponse;
import com.ms.petopia.api.reservation.dto.EntryQrResponse;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityResponse;
import com.ms.petopia.api.reservation.dto.ReservationListResponse;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateRequest;
import com.ms.petopia.api.reservation.dto.UpdateReservationVisitDateResponse;
import com.ms.petopia.api.reservation.service.ReservationAvailabilityService;
import com.ms.petopia.api.reservation.service.EntryQrService;
import com.ms.petopia.api.reservation.service.OnsiteReservationService;
import com.ms.petopia.api.reservation.service.ReservationQueryService;
import com.ms.petopia.api.reservation.service.ReservationService;
import com.ms.petopia.api.reservation.service.ReservationVisitDateChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;
    private final OnsiteReservationService onsiteReservationService;
    private final EntryQrService entryQrService;
    private final ReservationQueryService reservationQueryService;
    private final ReservationAvailabilityService reservationAvailabilityService;
    private final ReservationVisitDateChangeService visitDateChangeService;

    @GetMapping("/fairs/{fairId}/reservation-availability")
    public ReservationAvailabilityResponse getReservationAvailability(@PathVariable Long fairId) {
        return reservationAvailabilityService.getAvailability(fairId);
    }

    @GetMapping("/reservations/me")
    public ReservationListResponse getMyReservations(
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return reservationQueryService.getMyReservations(userId, page, size);
    }

    @PatchMapping("/reservations/{reservationId}/visit-date")
    public UpdateReservationVisitDateResponse changeVisitDate(
            @PathVariable Long reservationId,
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long userId,
            @RequestBody UpdateReservationVisitDateRequest request
    ) {
        return visitDateChangeService.changeVisitDate(reservationId, userId, request);
    }

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

    @PostMapping("/fairs/{fairId}/onsite-reservations")
    public ResponseEntity<CreateOnsiteReservationResponse> createOnsiteReservation(
            @PathVariable Long fairId,
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long userId,
            @RequestBody(required = false) CreateOnsiteReservationRequest request
    ) {
        // 무료 현장예매는 요청 본문 없이도 생성할 수 있다.
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(onsiteReservationService.create(fairId, userId, request));
    }

    @GetMapping("/reservations/{reservationId}/entry-qr")
    public EntryQrResponse getEntryQr(
            @PathVariable Long reservationId,
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long userId
    ) {
        return new EntryQrResponse(
                reservationId,
                entryQrService.issueForUser(reservationId, userId)
        );
    }
}
