package com.ms.petopia.api.refund.controller;

import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.service.RefundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;

    // 환불 요청 수신 + 처리(모의 환불이라 접수와 동시에 완료). 5경로(USER_CANCEL 등) 공용 —
    // refundReason으로 구분한다. actingUserId는 권한 검증용이 아니라 "누가 이 환불을 처리했는지"
    // 감사 추적용(다른 컨트롤러들과 헤더 관례 통일). 세밀한 권한 검증은 인증 도메인 완성 후
    // 추가 예정(TODO).
    @PostMapping("/payments/{paymentId}/refunds")
    public ResponseEntity<RefundResponse> refund(
            @PathVariable Long paymentId,
            @RequestHeader(RefundTemporaryAuthHeaders.USER_ID) Long actingUserId,
            @Valid @RequestBody RefundRequest request
    ) {
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
