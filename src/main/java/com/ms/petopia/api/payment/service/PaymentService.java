package com.ms.petopia.api.payment.service;

import com.ms.petopia.api.payment.client.TossPaymentClient;
import com.ms.petopia.api.payment.dto.*;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

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
    private final TossPaymentClient tossPaymentClient;

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

    /**
     * 참가비 결제를 생성한다. application 테이블은 조회하지 않으므로(애그리거트 간
     * ID 참조 원칙 유지) 금액·소속 정보는 호출자가 요청에 실어보낸 값을 그대로 신뢰한다.
     *
     * <p>동일 참가신청에 대한 중복 결제는 idempotencyKey(UK_PAYMENT_IDEMPOTENCY_KEY)로
     * DB가 막는다 — 여기서 잡아 {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE}로 변환한다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 이미 결제된 참가신청일 때
     */

    @Transactional
    public PaymentResponse payVendorFee(Long applicationId,Long userId, VendorFeePaymentRequest request) {
        LocalDateTime now = LocalDateTime.now();

        PaymentRow row = new PaymentRow();
        row.setPaymentType("VENDOR_FEE");
        row.setAmount(request.amount());
        row.setStatus("PENDING");
        row.setMethod("TOSS");
        row.setIdempotencyKey("VENDOR_FEE_" + applicationId);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setFairId(request.fairId());
        row.setBusinessId(request.businessId());
        row.setPayerUserId(userId);
        row.setApplicationId(applicationId);

        try {
            paymentMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        return PaymentResponse.from(row);
    }

    @Transactional
    public PaymentResponse confirmPayment(Long paymentId, ConfirmPaymentRequest request) {
        PaymentRow row = paymentMapper.selectById(paymentId);
        if (row == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        String orderId = "PAYMENT_" + row.getPaymentId();
        TossPaymentResponse tossResponse = tossPaymentClient.confirmPayment(
                request.paymentKey(), orderId, row.getAmount()
        );
        LocalDateTime now = LocalDateTime.now();
        row.setMethod(tossResponse.method());
        row.setTossPaymentKey(tossResponse.paymentKey());
        row.setPaidAt(now);
        row.setUpdatedAt(now);

        int updated = paymentMapper.markCompleted(row);
        if (updated == 0) {
            // 이 사이 다른 요청이 먼저 확정 처리한 경우(동시 승인 시도)
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        return PaymentResponse.from(row);

    }

}
