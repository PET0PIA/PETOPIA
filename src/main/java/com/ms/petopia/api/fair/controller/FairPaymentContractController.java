package com.ms.petopia.api.fair.controller;

import com.ms.petopia.api.fair.dto.FairCancellationStatusResponse;
import com.ms.petopia.api.fair.dto.FairOpeningFeePaymentContextResponse;
import com.ms.petopia.api.fair.service.FairCancellationStatusService;
import com.ms.petopia.api.fair.service.FairOpeningFeePaymentContextService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 다른 도메인이 행사 도메인에 묻는 내부 계약 API 모음. reservation 도메인의
 * {@code ReservationPaymentContractController}와 같은 역할·같은 경로 프리픽스를 쓴다.
 */
@RestController
@RequestMapping("/internal/api/v1")
@RequiredArgsConstructor
public class FairPaymentContractController {

    private final FairCancellationStatusService cancellationStatusService;
    private final FairOpeningFeePaymentContextService openingFeePaymentContextService;

    /**
     * 정산/결제 도메인이 정산 계산·확정 직전에 호출한다 - 취소된 행사면 그쪽에서 409로
     * 거절하는 걸 전제로 한다(판단은 이 도메인이 하고, 거절은 호출부 책임).
     */
    @GetMapping("/fairs/{fairId}/cancellation-status")
    public FairCancellationStatusResponse getCancellationStatus(
            @PathVariable Long fairId,
            @RequestHeader(FairTemporaryAuthHeaders.INTERNAL_CALLER) String caller
    ) {
        assertTemporaryPaymentCaller(caller);
        return cancellationStatusService.getCancellationStatus(fairId);
    }

    /**
     * 결제 도메인이 개설비 결제 생성 직전에 호출한다 - 클라이언트가 보낸 금액을 신뢰하지 않고
     * 승인 시 확정해 둔 금액을 그대로 내려준다({@link com.ms.petopia.api.fair.service.FairService#review}).
     */
    @GetMapping("/fairs/{fairId}/opening-fee-payment-context")
    public FairOpeningFeePaymentContextResponse getOpeningFeePaymentContext(
            @PathVariable Long fairId,
            @RequestHeader(FairTemporaryAuthHeaders.INTERNAL_CALLER) String caller
    ) {
        assertTemporaryPaymentCaller(caller);
        return openingFeePaymentContextService.getPaymentContext(fairId);
    }

    private void assertTemporaryPaymentCaller(String caller) {
        // TODO 결제 도메인 연동 방식이 확정되면 내부 인증 또는 직접 서비스 계약으로 교체한다.
        if (!FairTemporaryAuthHeaders.PAYMENT_CALLER.equals(caller)) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
    }
}
