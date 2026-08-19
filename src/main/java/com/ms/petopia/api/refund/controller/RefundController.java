package com.ms.petopia.api.refund.controller;

import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    // 환불 요청 수신 + 처리(모의 환불이라 접수와 동시에 완료). 5경로(USER_CANCEL 등) 공용 —
    // refundReason으로 구분한다. actingUserId는 "누가 이 환불을 처리했는지" 감사 추적용으로
    // refund()에 그대로 넘어가지만, 그 전에 assertRequesterAuthorized로 결제 소유자 또는
    // 그 행사 담당 관리자인지 먼저 확인한다 — refund() 자체는 안 건드린다(채린님/승훈님
    // 도메인이 시스템 명의(SYSTEM_ACTOR_USER_ID)로 직접 호출하는 내부 경로가 있어서).
    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> refund(
            @PathVariable Long paymentId,
            @AuthenticationPrincipal Long actingUserId,
            @Valid @RequestBody RefundRequest request
    ) {
        refundService.assertRequesterAuthorized(paymentId, actingUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(refundService.refund(paymentId, actingUserId, request));
    }

    // 결제 한 건에 걸린 환불 내역 조회. UK_REFUND_PAYMENT 제약상 결제당 환불은 최대 1건이라
    // 리스트지만 항상 0개 또는 1개다.
    @GetMapping("/payments/{paymentId}/refunds")
    public List<RefundResponse> getRefundsByPayment(@PathVariable Long paymentId) {
        RefundRow row = refundService.findByPaymentId(paymentId);
        return row == null ? Collections.emptyList() : List.of(RefundResponse.from(row));
    }

    // 환불 단건 상세 조회.
    @GetMapping("/refunds/{refundId}")
    public RefundResponse getRefund(@PathVariable Long refundId) {
        return refundService.getRefund(refundId);
    }
}
