package com.ms.petopia.api.settlement.controller;

import com.ms.petopia.api.settlement.dto.FairRevenueSummaryResponse;
import com.ms.petopia.api.settlement.dto.SettlementResponse;
import com.ms.petopia.api.settlement.service.SettlementExportService;
import com.ms.petopia.api.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;
    private final SettlementExportService settlementExportService;

    // 정산 계산(PENDING 생성, EVENT_ADMIN/SUPER_ADMIN). 원래는 "행사 종료 후" 자동 트리거가
    // 목표지만 스케줄러/이벤트 인프라가 아직 없어서 관리자가 수동으로 호출하는 걸로 대신한다
    // (TODO: 자동화). 그 행사 담당자인지는 SettlementService.calculate가 FairAdminAccessGuard로 확인한다.
    @PostMapping("/fairs/{fairId}/vendors/{businessId}/settlements")
    public ResponseEntity<SettlementResponse> calculate(
            @PathVariable Long fairId,
            @PathVariable Long businessId
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(settlementService.calculate(fairId, businessId));
    }

    // 정산 확정(EVENT_ADMIN/SUPER_ADMIN). 그 행사 담당자인지는 SettlementService.confirm이
    // FairAdminAccessGuard로 확인한다.
    @PutMapping("/settlements/{settlementId}/confirm")
    public SettlementResponse confirm(
            @PathVariable Long settlementId,
            @AuthenticationPrincipal Long userId
    ) {
        return settlementService.confirm(settlementId, userId);
    }

    // 정산 재계산(EVENT_ADMIN/SUPER_ADMIN). PENDING 상태에서만 가능 — 계산 이후 새로 완료된
    // 결제나(PENDING 정산에 포함된 채로 허용된) 환불을 최신 상태로 다시 반영한다. 그 행사
    // 담당자인지는 SettlementService.recalculate가 FairAdminAccessGuard로 확인한다.
    @PutMapping("/settlements/{settlementId}/recalculate")
    public SettlementResponse recalculate(@PathVariable Long settlementId) {
        return settlementService.recalculate(settlementId);
    }

    // 확정된 정산을 PENDING으로 되돌린다(SUPER_ADMIN 전용, SecurityConfig에서도 강제).
    // 확정 후 오류 정정 절차: reopen → recalculate → confirm 순서로 다시 거친다.
    @PutMapping("/settlements/{settlementId}/reopen")
    public SettlementResponse reopen(
            @PathVariable Long settlementId,
            @AuthenticationPrincipal Long userId
    ) {
        return settlementService.reopen(settlementId, userId);
    }

    // 정산 단건 조회(EVENT_ADMIN/SUPER_ADMIN). 원래 "참가업체 본인 조회"까지 염두에 둔
    // 경로지만, 실제로 붙어있는 프론트(SettlementPage.tsx)는 관리자 화면뿐이라 지금은
    // export와 동일하게 그 행사 담당 EVENT_ADMIN/SUPER_ADMIN만 허용한다(VENDOR 본인 열람은
    // 아직 스코프 밖 — 필요해지면 businessId 소유자 검증을 별도로 추가해야 한다).
    @GetMapping("/fairs/{fairId}/vendors/{businessId}/settlement")
    public SettlementResponse getSettlement(
            @PathVariable Long fairId,
            @PathVariable Long businessId
    ) {
        return settlementService.getByFairAndBusiness(fairId, businessId);
    }

    // 행사 관리자의 행사 전체 정산 목록 조회(EVENT_ADMIN/SUPER_ADMIN). 그 행사 담당자인지는
    // SettlementService.getByFair가 FairAdminAccessGuard로 확인한다.
    @GetMapping("/fairs/{fairId}/settlements")
    public List<SettlementResponse> getSettlementsByFair(@PathVariable Long fairId) {
        return settlementService.getByFair(fairId);
    }

    // 정산 통합검색(SUPER_ADMIN 전용, 2026-08-21). fairId·businessId 둘 다 선택적이고 최소
    // 하나는 채워야 한다 - SettlementService.getByFilter가 검증·권한확인 전부 담당한다.
    @GetMapping("/settlements")
    public List<SettlementResponse> searchSettlements(
            @RequestParam(required = false) Long fairId,
            @RequestParam(required = false) Long businessId
    ) {
        return settlementService.getByFilter(fairId, businessId);
    }

    // 행사 전체 정산내역 엑셀(.xlsx) 다운로드. statistics 도메인의 visit-stats/export와 동일 패턴.
    @GetMapping("/fairs/{fairId}/settlements/export")
    public ResponseEntity<byte[]> exportSettlements(@PathVariable Long fairId) throws IOException {
        byte[] body = settlementExportService.exportAsExcel(fairId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"settlements-" + fairId + ".xlsx\"")
                .header(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(body);
    }

    // 행사별 매출 요약 목록(SUPER_ADMIN 전용, WBS 5.6) - 위 정산(참가비 전용, 저장됨)과 별개로
    // 예약금+참가비를 합친 행사 전체 매출을 요율로 나눠서 보여주는 조회 전용 화면.
    @GetMapping("/settlements/revenue-summary")
    public List<FairRevenueSummaryResponse> getFairRevenueSummaries() {
        return settlementService.getFairRevenueSummaries();
    }

    // 행사 하나의 매출 요약(EVENT_ADMIN/SUPER_ADMIN, 2026-08-22) - 위와 같은 집계를 담당 행사
    // 하나로 좁힌 버전. 그 행사 담당자인지는 SettlementService.getFairRevenueSummary가
    // FairAdminAccessGuard로 확인한다.
    @GetMapping("/fairs/{fairId}/revenue-summary")
    public FairRevenueSummaryResponse getFairRevenueSummary(@PathVariable Long fairId) {
        return settlementService.getFairRevenueSummary(fairId);
    }

    // 행사별 매출 요약 엑셀(.xlsx) 다운로드(SUPER_ADMIN 전용).
    @GetMapping("/settlements/revenue-summary/export")
    public ResponseEntity<byte[]> exportFairRevenueSummaries() throws IOException {
        byte[] body = settlementExportService.exportRevenueSummaryAsExcel();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"settlement-revenue-summary.xlsx\"")
                .header(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(body);
    }
}
