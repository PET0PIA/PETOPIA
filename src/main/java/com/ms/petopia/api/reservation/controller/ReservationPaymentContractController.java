package com.ms.petopia.api.reservation.controller;

import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletedCommand;
import com.ms.petopia.api.reservation.dto.ReservationPaymentCompletionResponse;
import com.ms.petopia.api.reservation.dto.ReservationPaymentContextResponse;
import com.ms.petopia.api.reservation.service.ReservationPaymentCompletionService;
import com.ms.petopia.api.reservation.service.ReservationPaymentContextService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/api/v1")
@RequiredArgsConstructor
public class ReservationPaymentContractController {

    private final ReservationPaymentContextService contextService;
    private final ReservationPaymentCompletionService completionService;

    @GetMapping("/reservations/{reservationId}/payment-context")
    public ReservationPaymentContextResponse getPaymentContext(
            @PathVariable Long reservationId,
            @RequestHeader(TemporaryAuthHeaders.INTERNAL_CALLER) String caller
    ) {
        assertTemporaryPaymentCaller(caller);
        return contextService.getPayableContext(reservationId);
    }

    @PostMapping("/reservation-payment-completions")
    public ReservationPaymentCompletionResponse completePayment(
            @RequestHeader(TemporaryAuthHeaders.INTERNAL_CALLER) String caller,
            @RequestBody ReservationPaymentCompletedCommand command
    ) {
        assertTemporaryPaymentCaller(caller);
        return completionService.complete(command);
    }

    private void assertTemporaryPaymentCaller(String caller) {
        // TODO 결제 도메인 연동 방식이 확정되면 내부 인증 또는 직접 서비스 계약으로 교체한다.
        if (!TemporaryAuthHeaders.PAYMENT_CALLER.equals(caller)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
    }
}
