package com.ms.petopia.api.application.controller;

import com.ms.petopia.api.application.dto.response.ApplicationVendorFeePaymentContextResponse;
import com.ms.petopia.api.application.service.ApplicationPaymentContextService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 다른 도메인이 참가 신청 도메인에 묻는 내부 계약 API 모음. fair 도메인의
 * {@code FairPaymentContractController}와 같은 역할·같은 경로 프리픽스를 쓴다.
 */
@RestController
@RequestMapping("/internal/api/v1")
@RequiredArgsConstructor
public class ApplicationPaymentContractController {

    private final ApplicationPaymentContextService paymentContextService;

    /**
     * 결제 도메인이 참가비 결제 생성 직전에 호출한다 - 클라이언트가 보낸 금액을 신뢰하지 않고
     * 승인 시 확정해 둔 finalPrice를 그대로 내려준다.
     */
    @GetMapping("/applications/{applicationId}/vendor-fee-payment-context")
    public ApplicationVendorFeePaymentContextResponse getVendorFeePaymentContext(
            @PathVariable Long applicationId,
            @RequestHeader(ApplicationTemporaryAuthHeaders.INTERNAL_CALLER) String caller
    ) {

        assertTemporaryPaymentCaller(caller);
        return paymentContextService.getPayableContext(applicationId);

    }

    private void assertTemporaryPaymentCaller(String caller) {

        // TODO 결제 도메인 연동 방식이 확정되면 내부 인증 또는 직접 서비스 계약으로 교체한다.
        if (!ApplicationTemporaryAuthHeaders.PAYMENT_CALLER.equals(caller)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }

    }

}
