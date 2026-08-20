package com.ms.petopia.api.application.service;

import com.ms.petopia.api.application.domain.Application;
import com.ms.petopia.api.application.dto.response.ApplicationVendorFeePaymentContextResponse;
import com.ms.petopia.api.application.mapper.ApplicationMapper;
import com.ms.petopia.api.business.domain.Business;
import com.ms.petopia.api.business.mapper.BusinessMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/*
 * {@code ApplicationPaymentContractController}가 노출하는 참가비 결제 계약의 실제 판단 로직.
 * 결제 도메인이 더 이상 요청 바디의 금액을 신뢰하지 않고, 이 서비스가 내려주는 확정 금액만
 * 쓰도록 하는 게 목적이다(승인 시 저장한 finalPrice - {@code ApplicationService#approveApplication} 참고).
 */
@Service
@RequiredArgsConstructor
public class ApplicationPaymentContextService {

    private final ApplicationMapper applicationMapper;
    private final BusinessMapper businessMapper;

    /**
     * @throws CommonException {@link ErrorCode#APPLICATION_NOT_FOUND} 존재하지 않는 신청 ID일 때
     * @throws CommonException {@link ErrorCode#APPLICATION_NOT_PAYMENT_PENDING} 결제 대기 상태가
     *         아니거나, 금액이 아직 확정되지 않았거나, 사업자 정보를 찾을 수 없을 때
     */
    @Transactional(readOnly = true)
    public ApplicationVendorFeePaymentContextResponse getPayableContext(Long applicationId) {

        // applicationId가 null이거나 0 이하(음수 포함)면, 유효하지 않은 입력
        if (applicationId == null || applicationId <= 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }

        // 신청이 존재하는지
        Application application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_FOUND);
        }

        // 지금 결제 대기 상태(PAYMENT_PENDING)인지
        if (application.getStatus() != Application.Status.PAYMENT_PENDING) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
        }

        // finalPrice가 확정돼 있는지
        if (application.getFinalPrice() == null || application.getFinalPrice() <= 0) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
        }

        // 그 신청이 속한 사업자(business)가 실제로 존재하는지
        Business business = businessMapper.selectById(application.getBusinessId());
        if (business == null) {
            throw new CommonException(ErrorCode.APPLICATION_NOT_PAYMENT_PENDING);
        }

        return new ApplicationVendorFeePaymentContextResponse(
                application.getApplicationId(),
                application.getFairId(),
                application.getBusinessId(),
                business.getOwnerId(),
                application.getFinalPrice(),
                application.getPaymentDueAt()
        );

    }

}
