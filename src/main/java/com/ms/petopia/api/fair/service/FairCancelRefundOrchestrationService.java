package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairCancelRefundMapper;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 행사 취소 승인 뒤 관람객 예약금·참가업체 참가비를 일괄 환불한다(개설비는 관리자 수동
 * 처리 대상이라 제외 - {@link RefundReason#OPENING_FEE_MANUAL} 참고).
 *
 * <p>일부러 {@code @Transactional}을 달지 않는다. {@link RefundService#refund}는 결제
 * 한 건마다 자기 트랜잭션(결제 행 잠금 + 정산 재계산 표시)을 갖는데, 이 클래스에
 * {@code @Transactional}을 달면 같은 트랜잭션에 합류(REQUIRED)해버려서 한 건이라도
 * 예외를 던지면 그 트랜잭션이 rollback-only로 표시되고, 이미 처리된 나머지 환불까지
 * 커밋 시점에 {@code UnexpectedRollbackException}으로 전부 날아간다. 결제 건마다
 * 독립된 트랜잭션으로 처리해서, 한두 건이 실패해도(이미 환불됨, 정산 확정으로 거부됨 등)
 * 그 건만 건너뛰고 나머지는 계속 진행되게 한다 - 행사 취소 승인 자체는 이미 끝난
 * 사실이라 환불 일부 실패가 그걸 되돌릴 이유는 아니다.
 *
 * <p>{@code FairCancelRequestController}가 {@code review()} 호출(자체 트랜잭션)이
 * 커밋된 뒤에 이 메서드를 부르는 것을 전제로 한다 - 취소 승인이 실제로 반영되기 전에
 * 환불부터 나가면 안 되기 때문이다.
 *
 * <p>지금은 HTTP 요청 안에서 동기로 전부 처리한다(MVP 모의 환불 범위, 배치/큐 인프라
 * 없음). 결제 건수가 많은 행사는 이 요청이 오래 걸릴 수 있다 - 나중에 비동기 처리가
 * 필요해지면 그때 배치 잡으로 옮기는 걸 고려한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FairCancelRefundOrchestrationService {

    private final FairCancelRefundMapper cancelRefundMapper;
    private final FairMapper fairMapper;
    private final RefundService refundService;

    /**
     * 취소된 행사의 환불 대상 결제를 전부 환불 처리한다. 이미 환불된 건은 조회 자체에서
     * 빠지고({@code LEFT JOIN refund ... IS NULL}), 조회 이후 동시에 다른 경로로 이미
     * 환불됐다면 {@link RefundService#refund}가 예외를 던지므로 그 건만 건너뛴다.
     *
     * @return 실제로 환불 처리된 건수
     */
    public int refundForCanceledFair(Long fairId, Long actorUserId) {
        Fair fair = fairMapper.selectById(fairId);
        if (fair == null || fair.getCanceledAt() == null) {
            // 방어적 체크 - 호출부가 취소 승인 직후에만 부르는 걸 전제로 하지만, 실수로
            // 취소되지 않은 행사에 대해 불려도 조용히 0건으로 끝내고 환불을 내보내지 않는다.
            log.warn("취소되지 않은 행사에 환불 오케스트레이션이 호출됐습니다. fairId={}", fairId);
            return 0;
        }

        List<PaymentRow> refundablePayments = cancelRefundMapper.selectRefundablePaymentsByFairId(fairId);
        int refunded = 0;
        for (PaymentRow payment : refundablePayments) {
            RefundReason reason = reasonFor(payment.getPaymentType());
            if (reason == null) {
                continue;
            }
            try {
                refundService.refund(payment.getPaymentId(), actorUserId,
                        new RefundRequest(reason, RequestedByDomain.FAIR));
                refunded++;
            } catch (CommonException e) {
                // 이미 환불됨(동시 처리)/정산 확정으로 환불 불가 등 - 이 건만 건너뛰고 계속한다.
                log.warn("행사 취소 환불 처리 실패. fairId={}, paymentId={}, reason={}",
                        fairId, payment.getPaymentId(), e.getMessage());
            }
        }
        return refunded;
    }

    private RefundReason reasonFor(String paymentType) {
        return switch (paymentType) {
            case "RESERVATION_DEPOSIT" -> RefundReason.FAIR_CANCEL_USER;
            case "VENDOR_FEE" -> RefundReason.FAIR_CANCEL_VENDOR;
            default -> null; // 매퍼 쿼리가 이미 이 두 유형만 걸러오지만, 방어적으로 한 번 더 확인한다.
        };
    }
}
