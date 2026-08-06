package com.ms.petopia.api.payment.service;

import com.ms.petopia.api.payment.client.ReservationPaymentContractClient;
import com.ms.petopia.api.payment.client.TossPaymentClient;
import com.ms.petopia.api.payment.dto.*;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentMapper paymentMapper;
    private final TossPaymentClient tossPaymentClient;
    private final ReservationPaymentContractClient reservationPaymentContractClient;

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
     * 예약 ID로 그 예약의 예약금 결제를 조회한다. 예약 도메인이 취소 처리 중 환불 API를
     * 부르기 전에 paymentId를 알아내는 용도(동기 호출 흐름: 예약취소 -> 이 API로 paymentId
     * 조회 -> 환불 API 호출 -> 응답 받아서 예약 상태 전환).
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 그 예약으로 결제된 적이 없을 때
     */
    public PaymentResponse getByReservationId(Long reservationId) {
        PaymentRow row = paymentMapper.selectByReservationId(reservationId);
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

    /**
     * 예약금 결제를 생성한다. 참가비와 달리 금액을 클라이언트가 안 보내도 된다 —
     * 예약 도메인의 결제 컨텍스트 조회로 진짜 금액을 받아온다.
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 예약 소유자가 아닐 때
     * @throws CommonException {@link ErrorCode#PAYMENT_TARGET_NOT_PAYABLE} 예약이 결제 가능한 상태가 아닐 때
     */
    public PaymentResponse payReservationDeposit(Long reservationId, Long userId) {
        ReservationPaymentContext context = reservationPaymentContractClient.getPaymentContext(reservationId);
        if (!userId.equals(context.payerUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentRow row = new PaymentRow();
        row.setPaymentType("RESERVATION_DEPOSIT");
        row.setAmount(context.amount());
        row.setStatus("PENDING");
        row.setMethod("TOSS");
        row.setIdempotencyKey("RESERVATION_DEPOSIT_" + reservationId);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);
        row.setFairId(context.fairId());
        row.setPayerUserId(userId);
        row.setReservationId(reservationId);

        try {
            paymentMapper.insert(row);
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        return PaymentResponse.from(row);
    }


    // 이 메서드는 일부러 @Transactional을 안 붙인다. markProcessing()의 커밋이
    // 토스를 부르기 "전에" 완전히 끝나서 다른 동시 요청 눈에 즉시 보여야 하기 때문 —
    // 하나의 트랜잭션으로 묶어버리면 markProcessing 변경이 메서드가 끝날 때까지
    // 커밋 안 되고, 그 사이 다른 요청도 여전히 PENDING을 보고 똑같이 토스를 불러버릴
    // 수 있다. 각 markXxx 호출은 UPDATE 한 줄짜리라 그 자체로 원자적이라
    // 트랜잭션으로 묶지 않아도 개별 쓰기의 정합성은 깨지지 않는다.
    public PaymentResponse confirmPayment(Long paymentId, Long userId, ConfirmPaymentRequest request) {
        PaymentRow row = paymentMapper.selectById(paymentId);
        if (row == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!userId.equals(row.getPayerUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        // 토스를 부르기 전에 먼저 선점한다. 동시에 두 요청이 여기 도달해도 이 UPDATE는
        // 원자적이라 딱 하나만 1을 받는다 — 선점 실패(0)면 토스 호출 자체를 안 하고 끝낸다.
        int claimed = paymentMapper.markProcessing(paymentId, LocalDateTime.now());
        if (claimed == 0) {
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        String orderId = "PAYMENT_" + row.getPaymentId();
        TossPaymentResponse tossResponse;
        try {
            tossResponse = tossPaymentClient.confirmPayment(request.paymentKey(), orderId, row.getAmount());
        } catch (CommonException e) {
            // 토스가 확정적으로 거부한 경우(4xx)만 FAILED로 남긴다. 5xx(게이트웨이 장애)는
            // 실제로는 승인이 처리됐을 수도 있어 여기서 실패로 단정하지 않고 PROCESSING
            // 그대로 둔다 — 나중에 상태조회로 확인 후 재시도하는 흐름은 별도 구현 필요.
            if (e.getErrorCode() == ErrorCode.PAYMENT_APPROVAL_FAILED) {
                paymentMapper.markFailed(paymentId, LocalDateTime.now());
            }
            throw e;
        }
        LocalDateTime now = LocalDateTime.now();
        row.setStatus("COMPLETED");
        row.setMethod(tossResponse.method());
        row.setTossPaymentKey(tossResponse.paymentKey());
        row.setPaidAt(now);
        row.setUpdatedAt(now);

        int updated = paymentMapper.markCompleted(row);
        if (updated == 0) {
            // markProcessing으로 선점에 성공한 요청만 여기 도달하므로 이론상 발생하지
            // 않아야 하지만, 방어적으로 남겨둔다.
            throw new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        }

        if ("RESERVATION_DEPOSIT".equals(row.getPaymentType())) {
            notifyReservationDomain(row);
        }

        return PaymentResponse.from(row);
    }

    // eventId("PAYMENT_"+paymentId)가 매 시도마다 동일해서, 예약 도메인의 멱등 처리
    // 덕분에 재시도해도 안전하다(직전 시도가 실제로는 처리됐는데 응답만 유실된 경우도 커버).
    private static final int NOTIFY_MAX_ATTEMPTS = 3;
    private static final long NOTIFY_RETRY_DELAY_MS = 200;

    private void notifyReservationDomain(PaymentRow row) {
        for (int attempt = 1; attempt <= NOTIFY_MAX_ATTEMPTS; attempt++) {
            try {
                reservationPaymentContractClient.completePayment(
                        "PAYMENT_" + row.getPaymentId(),
                        row.getPaymentId(),
                        row.getReservationId(),
                        row.getAmount(),
                        row.getPaidAt()
                );
                return;
            } catch (Exception e) {
                if (attempt == NOTIFY_MAX_ATTEMPTS) {
                    // 결제 자체는 이미 성공했으므로 여기서 예외를 던져 결제 응답을 실패로 되돌리지 않는다.
                    // 재시도까지 다 실패하면 로그를 남겨서 운영 중 예약 확정 누락을 나중에 보정할 수 있게 한다.
                    // (배치/스케줄러로 자동 보정하는 건 별도 과제로 남겨둠 — 알려진 한계)
                    log.error("예약 도메인 결제완료 통지 {}회 재시도 모두 실패. reservationId={}, paymentId={}",
                            NOTIFY_MAX_ATTEMPTS, row.getReservationId(), row.getPaymentId(), e);
                    return;
                }
                log.warn("예약 도메인 결제완료 통지 실패({}번째 시도), 재시도한다. reservationId={}, paymentId={}",
                        attempt, row.getReservationId(), row.getPaymentId(), e);
                // 대기 중 인터럽트(취소 신호) 걸리면 재시도를 더 돌리지 않고 바로 빠져나간다.
                if (!sleepBeforeRetry()) {
                    return;
                }
            }
        }
    }

    private boolean sleepBeforeRetry() {
        try {
            Thread.sleep(NOTIFY_RETRY_DELAY_MS);
            return true;
        } catch (InterruptedException interruptedException) {
            // 인터럽트 상태를 삼키지 않고 다시 세팅 — 스레드 종료/취소 신호를 존중하기 위함.
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
