package com.ms.petopia.api.fairsettlement.controller;

import com.ms.petopia.api.fairsettlement.dto.FairSettlementResponse;
import com.ms.petopia.api.fairsettlement.service.FairSettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 행사별 최종정산(플랫폼 ↔ 행사) API(2026-08-22). 기존 SettlementController(업체별 정산,
 * {@code /fairs/{fairId}/vendors/{businessId}/settlements} 등)는 그대로 두고 건드리지 않는다.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FairSettlementController {

    private final FairSettlementService fairSettlementService;

    // 정산 계산(PENDING 생성, EVENT_ADMIN/SUPER_ADMIN). 그 행사 담당자인지는
    // FairSettlementService.calculate가 FairAdminAccessGuard로 확인한다.
    @PostMapping("/fairs/{fairId}/settlements/final")
    public ResponseEntity<FairSettlementResponse> calculate(@PathVariable Long fairId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(fairSettlementService.calculate(fairId));
    }

    // 정산 확정(EVENT_ADMIN/SUPER_ADMIN).
    @PutMapping("/settlements/final/{fairSettlementId}/confirm")
    public FairSettlementResponse confirm(
            @PathVariable Long fairSettlementId,
            @AuthenticationPrincipal Long userId
    ) {
        return fairSettlementService.confirm(fairSettlementId, userId);
    }

    // 정산 재계산(EVENT_ADMIN/SUPER_ADMIN).
    @PutMapping("/settlements/final/{fairSettlementId}/recalculate")
    public FairSettlementResponse recalculate(@PathVariable Long fairSettlementId) {
        return fairSettlementService.recalculate(fairSettlementId);
    }

    // 확정된 정산을 PENDING으로 되돌린다(SUPER_ADMIN 전용, SecurityConfig에서도 강제).
    @PutMapping("/settlements/final/{fairSettlementId}/reopen")
    public FairSettlementResponse reopen(
            @PathVariable Long fairSettlementId,
            @AuthenticationPrincipal Long userId
    ) {
        return fairSettlementService.reopen(fairSettlementId, userId);
    }

    // 행사 하나의 최종정산 단건 조회(EVENT_ADMIN/SUPER_ADMIN). 계산된 적 없으면 204.
    @GetMapping("/fairs/{fairId}/settlements/final")
    public ResponseEntity<FairSettlementResponse> getByFairId(@PathVariable Long fairId) {
        FairSettlementResponse response = fairSettlementService.getByFairId(fairId);
        return response == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(response);
    }
}
