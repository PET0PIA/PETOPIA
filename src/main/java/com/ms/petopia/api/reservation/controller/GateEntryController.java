package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.GateScanRequest;
import com.ms.petopia.api.reservation.dto.GateScanResponse;
import com.ms.petopia.api.reservation.service.GateEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/fairs/{fairId}/gate-entries")
@RequiredArgsConstructor
public class GateEntryController {

    private final GateEntryService gateEntryService;

    @PostMapping("/scan")
    public GateScanResponse scan(
            @PathVariable Long fairId,
            @AuthenticationPrincipal Long actorUserId,
            @RequestBody GateScanRequest request
    ) {
        return gateEntryService.scan(
                fairId,
                actorUserId,
                request.qrToken(),
                request.deviceInfo()
        );
    }
}
