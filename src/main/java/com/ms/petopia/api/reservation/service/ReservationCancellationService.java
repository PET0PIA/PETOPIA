package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.api.reservation.dto.CancelReservationRequest;
import com.ms.petopia.api.reservation.dto.CancelReservationResponse;
import com.ms.petopia.api.reservation.dto.ReservationCancellationContext;
import com.ms.petopia.api.reservation.mapper.ReservationCancellationMapper;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent; // 실시간 통계 확인용
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher; // 실시간 통계 확인용
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 관람객 본인의 예약 취소를 처리한다. 예약 상태에 따라 결제 쪽 후처리가 갈린다.
 *
 * <table>
 *   <caption>상태별 결제 후처리</caption>
 *   <tr><th>예약 상태</th><th>처리</th></tr>
 *   <tr><td>PENDING_PAYMENT</td>
 *       <td>딸린 PENDING 결제를 취소({@link PaymentService#cancelPayment}). 아직 받은 돈이
 *           없으니 환불 원장은 만들지 않는다.</td></tr>
 *   <tr><td>CONFIRMED, 예약금 0원</td><td>없음</td></tr>
 *   <tr><td>CONFIRMED, 예약금 &gt; 0원</td><td>전액 환불({@link RefundService#refund})</td></tr>
 * </table>
 *
 * <p>현장예매(ONSITE_DIRECT)는 자진취소 대상이 아니다 — 당일 현장에서 결제·입장하는 건이라
 * 취소 마감(입장 12시간 전) 규칙을 적용하면 사실상 항상 마감 초과다. 현장 관리자 처리로 남긴다.
 *
 * <p><b>왜 한 트랜잭션으로 묶는가</b>: 환불 MVP는 외부 PG 호출이 없는 "모의 환불"이라
 * ({@link RefundService} 참고) 환불이 결국 같은 DB에 REFUND 행 하나 쓰는 일이다. 그래서 예약
 * 상태 전이와 환불을 한 트랜잭션에 넣으면 "취소는 됐는데 환불은 안 된" 중간 상태가 아예
 * 생기지 않는다. 행사취소 일괄환불({@link com.ms.petopia.api.fair.service.FairCancelRefundOrchestrationService})
 * 처럼 작업행 + 스케줄러로 재시도하는 구조가 여기엔 필요 없는 이유도 이것 — 그쪽은 취소 승인이
 * 이미 커밋된 뒤에 환불을 시작해야 해서 재시도 장치가 필수였다. 실제 토스 결제취소 API가 붙어
 * 환불이 외부 호출을 타게 되면 이 전제가 깨지므로, 그때는 여기도 작업행 방식으로 바꿔야 한다.
 *
 * <p><b>주의</b>: 그래서 {@link RefundService#refundOrReuse}가 던지는 예외를 잡아서 취소를 계속
 * 진행하면 안 된다 — 같은 트랜잭션에 합류(REQUIRED)한 뒤 예외가 나면 트랜잭션이 rollback-only로
 * 표시돼, 잡고 커밋을 시도해봐야 {@code UnexpectedRollbackException}으로 끝난다. 환불이 거부되면
 * 취소도 같이 실패해서 예약이 CONFIRMED로 남는 게 맞는 동작이다.
 *
 * <p>같은 이유로 "이미 환불됐는지"를 여기서 미리 조회해 분기하지 않는다 — 조회와 환불 사이 창에서
 * 행사 취소 일괄환불이 커밋되면 그대로 롤백된다. 판단은 결제 행을 잠그는 refundOrReuse에 맡긴다.
 */
@Service
@RequiredArgsConstructor
public class ReservationCancellationService {

    private static final String PENDING_PAYMENT = "PENDING_PAYMENT";
    private static final String CONFIRMED = "CONFIRMED";
    private static final String ADVANCE = "ADVANCE";
    private static final String CANCELED = "CANCELED";
    private static final int DEFAULT_CANCEL_DEADLINE_HOURS = 12;

    /**
     * {@link PaymentService#cancelPayment}가 "이 도메인이 건드려도 되는 결제유형인지" 검증할 때 쓰는
     * 호출 도메인 값. RESERVATION은 RESERVATION_DEPOSIT 결제만 취소할 수 있다.
     */
    private static final String PAYMENT_CALLER_DOMAIN = "RESERVATION";

    /** 돈이 이미 움직였거나 움직이는 중이라, 예약만 취소해선 안 되는 결제 상태. */
    private static final List<String> PAYMENT_IN_FLIGHT_STATUSES = List.of("PROCESSING", "COMPLETED");

    private final ReservationCancellationMapper cancellationMapper;
    private final ReservationTimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher; // 실시간 통계 확인용
    private final PaymentMapper paymentMapper;
    private final PaymentService paymentService;
    private final RefundService refundService;

    /** 결제 전 예약, 무료 사전예약, 결제까지 끝난 유료 사전예약(전액 환불)을 취소한다. */
    @Transactional
    public CancelReservationResponse cancel(
            Long reservationId,
            Long userId,
            CancelReservationRequest request
    ) {
        validateRequest(reservationId, userId, request);

        ReservationCancellationContext reservation =
                cancellationMapper.selectCancellationContextForUpdate(reservationId);
        if (reservation == null) {
            throw new CommonException(ErrorCode.RESERVATION_NOT_FOUND);
        }
        if (!userId.equals(reservation.getUserId())) {
            throw new CommonException(ErrorCode.ACCESS_DENIED);
        }

        LocalDateTime now = timeProvider.now();
        validateCancelable(reservation, now);

        // 예약 상태를 바꾸기 전에 결제 쪽을 먼저 정리한다. 환불이 거부될 수 있는 케이스
        // (이미 확정된 정산에 포함된 결제 등)에서 예약 상태만 앞서 나가지 않도록 순서를 이렇게
        // 잡았다 — 한 트랜잭션이라 최종 결과는 같지만 흐름이 읽기 쉽다.
        RefundResponse refund = settlePaymentSide(reservation, userId);

        String reason = normalizeReason(request == null ? null : request.reason());
        int updated = cancellationMapper.cancelReservation(
                reservationId,
                reservation.getStatus(),
                reason,
                userId,
                now
        );
        if (updated != 1) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        cancellationMapper.insertCanceledHistory(
                reservationId,
                reservation.getStatus(),
                reason,
                userId,
                refund == null ? null : refund.refundId(),
                refund == null ? null : refund.refundAmount(),
                now
        );

        eventPublisher.publishEvent(new ReservationStatusChangedEvent(reservation.getFairId())); // 실시간 통계 확인용

        if (refund == null) {
            return new CancelReservationResponse(reservationId, CANCELED, now, false, null, null, null);
        }
        return new CancelReservationResponse(
                reservationId,
                CANCELED,
                now,
                true,
                refund.refundId(),
                refund.refundAmount(),
                refund.status()
        );
    }

    /**
     * 취소되는 예약에 딸린 결제를 정리한다.
     *
     * @return 환불을 처리했으면 그 환불, 환불 대상이 아니면 null
     */
    private RefundResponse settlePaymentSide(ReservationCancellationContext reservation, Long userId) {
        if (PENDING_PAYMENT.equals(reservation.getStatus())) {
            cancelPendingPayment(reservation);
            return null;
        }
        if (reservation.getReservationAmount() <= 0) {
            return null;
        }
        return refundDeposit(reservation, userId);
    }

    /**
     * 결제 전 예약에 딸린 PENDING 결제를 함께 취소한다. 이 정리를 빼면 예약은 취소됐는데 결제창을
     * 이미 띄워둔 사용자가 그대로 결제를 완료해, 취소된 예약에 돈이 들어오는 상태가 된다.
     *
     * <p>PROCESSING(토스 승인 진행 중)이나 COMPLETED면 돈이 이미 움직인 뒤라 예약만 취소해선
     * 안 된다 — 취소를 거부하고 잠시 후 재시도를 안내한다. 결제 완료 통지가 들어오면 예약이
     * CONFIRMED로 올라가고, 그다음 취소 요청은 환불 경로({@link #refundDeposit})를 탄다.
     */
    private void cancelPendingPayment(ReservationCancellationContext reservation) {
        PaymentRow payment = paymentMapper.selectByReservationId(reservation.getReservationId());
        if (payment == null) {
            // 결제를 아직 시작하지도 않은 예약 — 정리할 결제가 없다.
            return;
        }
        if (PAYMENT_IN_FLIGHT_STATUSES.contains(payment.getStatus())) {
            throw new CommonException(ErrorCode.RESERVATION_PAYMENT_IN_PROGRESS);
        }
        if (!"PENDING".equals(payment.getStatus())) {
            // FAILED/CANCELED/EXPIRED — 이미 끝난 결제라 손댈 게 없다.
            return;
        }
        // 이 사이 상태가 또 바뀌었으면(동시 결제 승인) cancelPayment가 PAYMENT_TARGET_NOT_PAYABLE로
        // 거부하고, 그 예외가 그대로 올라가 예약 취소도 함께 롤백된다.
        paymentService.cancelPayment(payment.getPaymentId(), PAYMENT_CALLER_DOMAIN);
    }

    /**
     * 결제까지 끝난 예약금을 전액 환불한다.
     *
     * <p>{@link RefundService#refund}가 아니라 {@link RefundService#refundOrReuse}를 쓴다.
     * 이미 환불된 결제(행사 취소 일괄환불이 먼저 처리한 경우)를 여기서 미리 조회해 걸러내는 방식은
     * 조회와 환불 사이에 창이 남는다 — 그 사이 일괄환불이 커밋하면 REFUND_ALREADY_PROCESSED로
     * 이 트랜잭션이 통째로 롤백돼 "환불은 됐는데 예약은 CONFIRMED로 남는" 상태가 된다.
     * refundOrReuse는 결제 행을 잠근 뒤 재사용/생성을 판단하므로 그 창이 없다.
     *
     * <p>결제가 COMPLETED인지, 정산에 묶여 환불 불가인지도 같은 잠금 구간 안에서 판단된다
     * (REFUND_TARGET_NOT_REFUNDABLE). 여기서 미리 검사하면 잠금 없이 읽는 셈이라 부정확하다.
     */
    private RefundResponse refundDeposit(ReservationCancellationContext reservation, Long userId) {
        PaymentRow payment = paymentMapper.selectByReservationId(reservation.getReservationId());
        if (payment == null) {
            throw new CommonException(ErrorCode.RESERVATION_REFUND_PAYMENT_NOT_FOUND);
        }
        return refundService.refundOrReuse(
                payment.getPaymentId(),
                userId,
                new RefundRequest(RefundReason.USER_CANCEL, RequestedByDomain.RESERVATION)
        );
    }

    private void validateRequest(Long reservationId, Long userId, CancelReservationRequest request) {
        if (reservationId == null || reservationId <= 0 || userId == null || userId <= 0
                || (request != null && request.reason() != null && request.reason().length() > 500)) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void validateCancelable(ReservationCancellationContext reservation, LocalDateTime now) {
        if (PENDING_PAYMENT.equals(reservation.getStatus())) {
            return;
        }
        if (!CONFIRMED.equals(reservation.getStatus()) || !ADVANCE.equals(reservation.getReservationType())) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }
        if (reservation.getVisitDate() == null || reservation.getEntryStartTime() == null) {
            throw new CommonException(ErrorCode.RESERVATION_STATUS_CONFLICT);
        }

        int deadlineHours = reservation.getCancelDeadlineHours() == null
                ? DEFAULT_CANCEL_DEADLINE_HOURS
                : reservation.getCancelDeadlineHours();
        if (deadlineHours < 0) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        LocalDateTime deadline = LocalDateTime.of(reservation.getVisitDate(), reservation.getEntryStartTime())
                .minusHours(deadlineHours);
        if (now.isAfter(deadline)) {
            throw new CommonException(ErrorCode.RESERVATION_CANCEL_DEADLINE_EXCEEDED);
        }
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return reason.trim();
    }
}
