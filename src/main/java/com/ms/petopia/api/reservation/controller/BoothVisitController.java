package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.BoothScanRequest;
import com.ms.petopia.api.reservation.dto.BoothScanResponse;
import com.ms.petopia.api.reservation.service.BoothVisitService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vendor/booths/{boothId}/booth-visits")
@RequiredArgsConstructor
public class BoothVisitController {

    private final BoothVisitService boothVisitService;

    @PostMapping("/scan")
    public BoothScanResponse scan(
            @PathVariable Long boothId,
            @AuthenticationPrincipal Long actorUserId,
            @RequestBody BoothScanRequest request
    ) {
        return boothVisitService.scan(boothId, actorUserId, request.qrToken());
    }
}
