package com.ms.petopia.api.payment.controller;

import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * 다른 도메인의 취소/만료 배치가 부르는 내부 계약 API(WBS 1.7). 사람이 아니라 서버가 호출하는
 * 거라 {@code /api/payments/...}(최종사용자용)와 분리해서 {@code /internal/api/v1}로 뒀다
 * (예약 도메인의 {@code ReservationPaymentContractController}와 동일한 패턴).
 */
@RestController
@RequestMapping("/internal/api/v1")
@RequiredArgsConstructor
public class PaymentContractController {

    private static final Set<String> ALLOWED_CALLERS = Set.of(
            PaymentInternalAuthHeaders.RESERVATION_CALLER,
            PaymentInternalAuthHeaders.FAIR_CALLER,
            PaymentInternalAuthHeaders.VENDOR_APPLICATION_CALLER
    );

    private final PaymentService paymentService;

    @PutMapping("/payments/{paymentId}/cancel")
    public PaymentResponse cancelPayment(
            @PathVariable Long paymentId,
            @RequestHeader(PaymentInternalAuthHeaders.INTERNAL_CALLER) String caller
    ) {
        assertKnownCaller(caller);
        return paymentService.cancelPayment(paymentId, caller);
    }

    @PutMapping("/payments/{paymentId}/expire")
    public PaymentResponse expirePayment(
            @PathVariable Long paymentId,
            @RequestHeader(PaymentInternalAuthHeaders.INTERNAL_CALLER) String caller
    ) {
        assertKnownCaller(caller);
        return paymentService.expirePayment(paymentId, caller);
    }

    private void assertKnownCaller(String caller) {
        if (!ALLOWED_CALLERS.contains(caller)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
    }
}
