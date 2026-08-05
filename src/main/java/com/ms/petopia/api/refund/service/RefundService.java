package com.ms.petopia.api.refund.service;

import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 환불 처리 서비스.
 *
 * <p>MVP는 "모의 환불"이다 — 실제 토스 결제취소 API 연동 없이, 요청이 들어오면 그 자리에서
 * 바로 COMPLETED로 확정한다(REQUESTED 같은 중간 대기 상태 없음). 실제 PG 취소 연동은
 * 별도 과제로 남겨둔다([[project_petopia_payment_domain]] 참고).
 *
 * <p>환불 완료를 요청 도메인(예약·참가업체·행사)에 통지하는 콜백은 이번 스코프에서 만들지 않는다
 * — 예약 도메인 쪽 수신 API가 아직 없어서(ReservationCancellationService.java 참고,
 * "TODO 결제 도메인의 환불 가능 여부 확인 및 환불 성공 통지 후 CANCELED로 전환" 상태),
 * 지금은 REFUND 테이블을 source of truth로 남기고 다른 도메인이 조회해서 확인하는 걸 전제로 한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private static final String COMPLETED = "COMPLETED";

    private final RefundMapper refundMapper;
    private final PaymentMapper paymentMapper;

    /**
     * 결제 한 건에 대한 환불을 접수하고 그 자리에서 바로 처리(모의 환불)한다.
     *
     * @param actingUserId 이 환불을 처리한 사용자(관리자 등). REFUND 테이블에는 별도 컬럼이 없어
     *                     아직 저장하지 않고 로그로만 남긴다 — 감사로그(AuditLog) 연동은 후속 과제.
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 대상 결제가 없을 때
     * @throws CommonException {@link ErrorCode#REFUND_TARGET_NOT_REFUNDABLE} 결제가 COMPLETED 상태가 아닐 때
     * @throws CommonException {@link ErrorCode#REFUND_ALREADY_PROCESSED} 이미 환불이 접수된 결제일 때
     */
    public RefundResponse refund(Long paymentId, Long actingUserId, RefundRequest request) {
        PaymentRow payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!COMPLETED.equals(payment.getStatus())) {
            throw new CommonException(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        RefundRow row = new RefundRow();
        row.setPaymentId(paymentId);
        row.setRefundReason(request.refundReason());
        row.setRequestedByDomain(request.requestedByDomain());
        // MVP는 전액환불 고정 — 부분환불은 2차 범위([[project_petopia_payment_domain]] 참고).
        row.setRefundAmount(payment.getAmount());
        row.setStatus(COMPLETED);
        row.setRequestedAt(now);
        row.setProcessedAt(now);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        try {
            refundMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.REFUND_ALREADY_PROCESSED);
        }

        log.info("환불 처리됨. refundId={}, paymentId={}, actingUserId={}, reason={}",
                row.getRefundId(), paymentId, actingUserId, request.refundReason());

        return RefundResponse.from(row);
    }

    /**
     * 환불 PK로 상세 조회한다.
     *
     * @throws CommonException {@link ErrorCode#REFUND_NOT_FOUND} 존재하지 않는 환불 ID일 때
     */
    public RefundResponse getRefund(Long refundId) {
        RefundRow row = refundMapper.selectById(refundId);
        if (row == null) {
            throw new CommonException(ErrorCode.REFUND_NOT_FOUND);
        }
        return RefundResponse.from(row);
    }

    /**
     * 결제 PK로 환불 내역을 조회한다. 환불된 적 없으면 null을 반환한다
     * (결제 상세 화면에서 "환불 여부"를 부가정보로 붙일 때 존재 유무 판단이 서비스 책임이 아니라서
     * 예외 대신 null로 남겨두고, 정산 집계 로직도 이 메서드를 그대로 재사용한다).
     */
    public RefundRow findByPaymentId(Long paymentId) {
        return refundMapper.selectByPaymentId(paymentId);
    }
}
