package com.ms.petopia.api.settlement.controller;

import com.ms.petopia.api.settlement.dto.SettlementResponse;
import com.ms.petopia.api.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    // 정산 계산(PENDING 생성). 원래는 "행사 종료 후" 자동 트리거가 목표지만 스케줄러/이벤트
    // 인프라가 아직 없어서 관리자가 수동으로 호출하는 걸로 대신한다(TODO: 자동화).
    @PostMapping("/fairs/{fairId}/vendors/{businessId}/settlements")
    public ResponseEntity<SettlementResponse> calculate(
            @PathVariable Long fairId,
            @PathVariable Long businessId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(settlementService.calculate(fairId, businessId));
    }

    // 정산 확정(SUPER_ADMIN). role 검증은 인증 도메인 완성 후 추가 예정(TODO) —
    // 지금은 헤더로 받은 userId를 확정자로 그대로 기록만 한다.
    @PutMapping("/settlements/{settlementId}/confirm")
    public SettlementResponse confirm(
            @PathVariable Long settlementId,
            @RequestHeader(SettlementTemporaryAuthHeaders.USER_ID) Long userId
    ) {
        return settlementService.confirm(settlementId, userId);
    }

    // 정산 재계산(관리자). PENDING 상태에서만 가능 — 계산 이후 새로 완료된 결제나
    // (PENDING 정산에 포함된 채로 허용된) 환불을 최신 상태로 다시 반영한다. role 검증은
    // confirm과 마찬가지로 인증 도메인 완성 후 추가 예정(TODO).
    @PutMapping("/settlements/{settlementId}/recalculate")
    public SettlementResponse recalculate(@PathVariable Long settlementId) {
        return settlementService.recalculate(settlementId);
    }

    // 참가업체 본인의 정산 단건 조회.
    @GetMapping("/fairs/{fairId}/vendors/{businessId}/settlement")
    public SettlementResponse getSettlement(
            @PathVariable Long fairId,
            @PathVariable Long businessId
    ) {
        return settlementService.getByFairAndBusiness(fairId, businessId);
    }

    // 박람회관리자의 행사 전체 정산 목록 조회.
    @GetMapping("/fairs/{fairId}/settlements")
    public List<SettlementResponse> getSettlementsByFair(@PathVariable Long fairId) {
        return settlementService.getByFair(fairId);
    }
}
