package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.GateScanRequest;
import com.ms.petopia.api.reservation.dto.GateScanResponse;
import com.ms.petopia.api.reservation.service.GateEntryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
            @RequestHeader(TemporaryAuthHeaders.USER_ID) Long actorUserId,
            @RequestBody GateScanRequest request
    ) {
        // TODO 인증 도메인 완성 후 actorUserId를 인증 Principal에서 가져온다.
        return gateEntryService.scan(
                fairId,
                actorUserId,
                request.qrToken(),
                request.gateName(),
                request.deviceInfo()
        );
    }
}
