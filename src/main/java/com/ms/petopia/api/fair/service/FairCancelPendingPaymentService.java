package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.payment.dto.PaymentListResponse;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.global.exception.CommonException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 취소된 행사에 딸린 PENDING 예약금·참가비 결제를 취소한다({@code PaymentService
 * #CALLER_PAYMENT_TYPES}에서 FAIR 캐스터가 이 두 유형까지 다룰 수 있게 확장한 권한을 실제로
 * 쓰는 쪽). 이미 완료(COMPLETED)된 결제를 환불하는 {@link FairCancelRefundOrchestrationService}와
 * 같은 방향("Fair 도메인이 취소된 행사의 결제 뒷정리를 전담")이지만, 별도 클래스로 뒀다.
 *
 * <p><b>왜 그 클래스처럼 영속 작업 테이블(재시도 추적)이 필요 없는가</b>: 환불 대상은 COMPLETED
 * 결제인데, 이 상태는 업무 흐름상 나중에 다른 값으로 바뀔 수 있어(예: 정산 확정 등) 발견 시점을
 * 놓치면 다시 스캔해도 영영 못 찾을 위험이 있었다 - 그래서 fair_cancel_refund_targets에
 * 영속화하고 재시도 횟수까지 추적한다. 반면 여기서 다루는 PENDING 결제는 우리가 취소에
 * 성공하지 못하는 한 계속 PENDING 그대로 남는다 - "아직 처리 못한 대상"이 다음 스케줄에도
 * 똑같이 다시 발견된다는 게 상태 자체로 보장되므로, 별도 작업 테이블 없이 "취소된 행사를
 * 매번 다시 훑어서 그 순간 PENDING인 것만 취소" 방식으로도 안전하다(멱등 - 이미 취소된
 * 결제는 {@code PaymentService#cancelPayment}의 status=PENDING 조건에서 자연히 걸러진다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FairCancelPendingPaymentService {

    private static final int PAYMENT_PAGE_SIZE = 100;

    /** Fair 도메인이 대신 취소해줄 결제유형. 개설비(FAIR_OPENING_FEE)는 행사 신청자 본인의
     * 결제라 여기서 자동 정리 대상으로 다루지 않는다(관리자 수동 처리). */
    private static final Set<String> PENDING_CANCELABLE_TYPES = Set.of("RESERVATION_DEPOSIT", "VENDOR_FEE");

    private final FairMapper fairMapper;
    private final PaymentService paymentService;

    /**
     * @return 이번 호출에서 새로 취소 처리한 결제 건수
     */
    public int cancelPendingPayments(int fairBatchSize) {
        List<Long> fairIds = fairMapper.selectCanceledFairIds(fairBatchSize);
        int canceled = 0;
        for (Long fairId : fairIds) {
            canceled += cancelForFair(fairId);
        }
        return canceled;
    }

    private int cancelForFair(Long fairId) {
        int canceled = 0;
        for (String paymentType : PENDING_CANCELABLE_TYPES) {
            try {
                canceled += cancelForType(fairId, paymentType);
            } catch (RuntimeException e) {
                // 이 결제유형만 실패로 남기고 다음 유형·다음 행사로 계속 진행한다 - PENDING으로
                // 남아있는 한 다음 스케줄에서 자연히 다시 시도된다.
                log.warn("행사 취소 PENDING 결제 취소 실패. fairId={}, paymentType={}", fairId, paymentType, e);
            }
        }
        return canceled;
    }

    /**
     * 같은 (fairId, paymentType, PENDING) 조건으로 반복 조회하며 취소한다. 페이지를 앞으로
     * 넘기는(page++) 방식을 쓰지 않는 이유: 이 메서드는 조회 대상 자체를 취소로 바꿔버려서
     * (조회 필터가 PENDING인데 취소하면 CANCELED가 됨) 일반적인 offset 페이징과 같이 쓰면
     * 방금 취소한 행이 결과에서 빠지면서 다음 페이지가 원래 있어야 할 행을 건너뛰는 문제가
     * 생긴다. 매번 0페이지를 다시 조회하면 취소한 만큼 자연히 줄어들어 이 문제가 없다.
     *
     * <p>한 페이지 전체가 취소에 실패하면(재시도해도 똑같이 실패할 결제가 섞여있을 수 있음)
     * 무한 루프를 피하려고 멈춘다 - 남은 건 다음 스케줄에서 다시 시도된다.
     */
    private int cancelForType(Long fairId, String paymentType) {
        int canceled = 0;
        while (true) {
            PaymentListResponse response =
                    paymentService.getPayments(fairId, null, paymentType, "PENDING", 0, PAYMENT_PAGE_SIZE);
            if (response.content().isEmpty()) {
                break;
            }

            int canceledThisRound = 0;
            for (PaymentResponse payment : response.content()) {
                if (cancelOne(payment.paymentId())) {
                    canceledThisRound++;
                }
            }
            canceled += canceledThisRound;

            if (canceledThisRound == 0) {
                break;
            }
        }
        return canceled;
    }

    private boolean cancelOne(Long paymentId) {
        try {
            paymentService.cancelPayment(paymentId, "FAIR");
            return true;
        } catch (CommonException e) {
            // 그 사이 다른 요청(사용자 결제 confirm 등)이 먼저 상태를 바꿔버린 경우 등 -
            // 다음 조회에서는 이미 PENDING이 아니라서 다시 안 걸린다.
            log.warn("PENDING 결제 취소 실패. paymentId={}", paymentId, e);
            return false;
        }
    }
}
