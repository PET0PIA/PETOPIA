package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.payment.dto.PaymentListResponse;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.service.RefundService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 행사 취소 승인 뒤 관람객 예약금·참가업체 참가비를 일괄 환불한다(개설비는 관리자 수동
 * 처리 대상이라 제외 - {@link RefundReason#OPENING_FEE_MANUAL} 참고).
 *
 * <p>환불 대상 조회는 결제 도메인 테이블을 직접 SQL로 읽지 않고, 결제 도메인이 이미 갖고
 * 있는 {@link PaymentService#getPayments}를 그대로 호출한다(결제 도메인 담당자 확인,
 * 2026-08-07) - 이 도메인이 payment 테이블 스키마에 직접 의존하지 않게 된다. 이 메서드가
 * 페이지네이션돼 있어서 끝까지 순회한다({@link #REFUND_PAGE_SIZE} 단위).
 *
 * <p>결제가 아직 COMPLETED로 끝나지 않고 PENDING으로 남아있는 건은 이 메서드로 처리하지
 * 않는다 - 결제 도메인이 별도로 제공할 예정인 결제 취소/만료 API(cancelPayment, 2026-08-07
 * 기준 아직 코드에 없음)로 처리해야 한다는 걸 확인했다. 그 API가 나오기 전까지 PENDING
 * 결제는 이 오케스트레이션의 범위 밖이다(남은 작업으로 별도 추적).
 *
 * <p>일부러 {@code @Transactional}을 달지 않는다. {@link RefundService#refund}는 결제
 * 한 건마다 자기 트랜잭션(결제 행 잠금 + 정산 재계산 표시)을 갖는데, 이 클래스에
 * {@code @Transactional}을 달면 같은 트랜잭션에 합류(REQUIRED)해버려서 한 건이라도
 * 예외를 던지면 그 트랜잭션이 rollback-only로 표시되고, 이미 처리된 나머지 환불까지
 * 커밋 시점에 {@code UnexpectedRollbackException}으로 전부 날아간다. 결제 건마다
 * 독립된 트랜잭션으로 처리해서, 한두 건이 실패해도(이미 환불됨, 정산 확정으로 거부됨,
 * 그 외 예상 못한 예외 등) 그 건만 건너뛰고 나머지는 계속 진행되게 한다 - 행사 취소 승인
 * 자체는 이미 끝난 사실이라 환불 일부 실패가 그걸 되돌릴 이유는 아니다.
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

    private static final int REFUND_PAGE_SIZE = 100;

    /** 자동 환불 대상 결제유형 -> 환불 사유. 개설비는 의도적으로 포함하지 않는다. */
    private static final Map<String, RefundReason> REFUNDABLE_TYPES = Map.of(
            "RESERVATION_DEPOSIT", RefundReason.FAIR_CANCEL_USER,
            "VENDOR_FEE", RefundReason.FAIR_CANCEL_VENDOR
    );

    private final FairMapper fairMapper;
    private final PaymentService paymentService;
    private final RefundService refundService;

    /**
     * 취소된 행사의 환불 대상 결제(COMPLETED 예약금·참가비)를 전부 환불 처리한다.
     * 이미 환불된 건이나 그 외 예상 못한 이유로 {@link RefundService#refund}가 예외를
     * 던지면 그 건만 건너뛴다.
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

        int refunded = 0;
        for (Map.Entry<String, RefundReason> entry : REFUNDABLE_TYPES.entrySet()) {
            refunded += refundAllOfType(fairId, actorUserId, entry.getKey(), entry.getValue());
        }
        return refunded;
    }

    /**
     * 결제유형 하나에 대해 COMPLETED 결제를 페이지 끝까지 순회하며 환불한다.
     * {@code PaymentService.getPayments}가 페이지네이션돼 있어서 1페이지만 보고 끝내면
     * 결제 건수 많은 행사에서 일부가 빠질 수 있다 - totalPages까지 다 돈다.
     */
    private int refundAllOfType(Long fairId, Long actorUserId, String paymentType, RefundReason reason) {
        int refunded = 0;
        int page = 0;
        int totalPages = 1;
        while (page < totalPages) {
            PaymentListResponse response =
                    paymentService.getPayments(fairId, null, paymentType, "COMPLETED", page, REFUND_PAGE_SIZE);
            totalPages = response.totalPages();

            for (PaymentResponse payment : response.content()) {
                try {
                    refundService.refund(payment.paymentId(), actorUserId,
                            new RefundRequest(reason, RequestedByDomain.FAIR));
                    refunded++;
                } catch (RuntimeException e) {
                    // 이미 환불됨(동시 처리)/정산 확정으로 환불 불가(CommonException) 뿐 아니라,
                    // 결제 도메인 쪽 데이터 접근 예외 등 예상 못한 RuntimeException도 이 건만
                    // 건너뛰고 나머지 결제는 계속 처리한다.
                    log.warn("행사 취소 환불 처리 실패. fairId={}, paymentId={}, reason={}",
                            fairId, payment.paymentId(), e.getMessage(), e);
                }
            }
            page++;
        }
        return refunded;
    }
}
