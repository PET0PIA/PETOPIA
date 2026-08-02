package com.ms.petopia.api.payment.service;

import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 결제 조회를 담당하는 서비스.
 *
 * <p>결제 생성(예약금/참가비/개설비 결제 처리)은 별도 API로 분리될 예정이라 이 클래스에는
 * 아직 조회 기능만 둔다.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;

    /**
     * 결제 ID로 상세 조회한다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 존재하지 않는 결제 ID일 때
     */
    public PaymentResponse getPayment(Long paymentId) {
        PaymentRow row = paymentMapper.selectById(paymentId);
        if (row == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        return PaymentResponse.from(row);
    }
}
