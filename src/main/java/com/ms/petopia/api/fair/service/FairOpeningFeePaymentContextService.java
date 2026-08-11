package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.dto.FairOpeningFeePaymentContextResponse;
import com.ms.petopia.api.fair.dto.FairStatus;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code FairPaymentContractController}가 노출하는 개설비 결제 계약의 실제 판단 로직.
 * 결제 도메인이 더 이상 요청 바디의 금액을 신뢰하지 않고, 이 서비스가 내려주는 확정 금액만
 * 쓰도록 하는 게 목적이다({@link FairService#review}에서 승인 시 저장한 opening_fee_amount).
 */
@Service
@RequiredArgsConstructor
public class FairOpeningFeePaymentContextService {

    private final FairMapper fairMapper;
    private final FairTimeProvider timeProvider;

    /**
     * @throws CommonException {@link ErrorCode#FAIR_NOT_FOUND} 존재하지 않는 행사 ID일 때
     * @throws CommonException {@link ErrorCode#FAIR_OPENING_FEE_NOT_PAYABLE} 개설비 결제 대기 상태가
     *         아니거나, 금액이 아직 확정되지 않았거나, 결제 기한이 이미 지났을 때(자동 만료
     *         스케줄러가 아직 EXPIRED로 못 바꾼 경합 구간을 여기서도 한 번 더 막는다)
     */
    @Transactional(readOnly = true)
    public FairOpeningFeePaymentContextResponse getPaymentContext(Long fairId) {
        if (fairId == null || fairId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        Fair fair = fairMapper.selectById(fairId);
        if (fair == null) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }
        if (fair.getStatus() != FairStatus.PAYMENT_PENDING
                || fair.getOpeningFeeAmount() == null || fair.getOpeningFeeAmount() <= 0
                || fair.getPaymentDueAt() == null || !timeProvider.now().isBefore(fair.getPaymentDueAt())) {
            throw new CommonException(ErrorCode.FAIR_OPENING_FEE_NOT_PAYABLE);
        }
        return new FairOpeningFeePaymentContextResponse(fairId, fair.getOpeningFeeAmount(), fair.getPaymentDueAt());
    }
}
