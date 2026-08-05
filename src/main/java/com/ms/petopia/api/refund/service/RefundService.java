package com.ms.petopia.api.refund.service;

import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.api.settlement.mapper.SettlementMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

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
 *
 * <p><b>알려진 한계</b>: 정산(Settlement) 계산 이후 환불이 들어오면 이미 만들어진 정산 금액을
 * 재계산하는 기능이 없다(CodeRabbit 리뷰 지적, PR #47). 완전한 재계산/정정 정산 절차는 무겁고
 * 이번 스코프 밖이라, 대신 "이미 정산에 포함된 결제는 환불 자체를 거부"하는 최소 방어만 둔다 —
 * 정산 금액이 조용히 틀려지는 것(데이터 부정합)만 막고, 정말 그 결제를 환불해야 하는 예외 상황은
 * 정산 담당자가 수동으로 처리하는 걸 전제로 한다.
 *
 * <p><b>동시성</b>: {@link #refund}와 {@code SettlementService.calculate}가 동시에 같은
 * 결제를 건드리면(정산 계산이 이 결제를 포함시키는 도중 환불이 끼어드는 경우) 정산 금액이
 * 환불 반영 전 값으로 굳을 수 있었다(CodeRabbit 리뷰 지적, PR #47). 그래서 둘 다 같은
 * {@code PAYMENT} 행을 {@code FOR UPDATE}로 잠그고 트랜잭션 안에서 처리하도록 맞춰서,
 * 어느 쪽이 먼저 시작하든 나머지 하나가 끝날 때까지 기다렸다가 최신 상태를 보고 진행한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private static final String COMPLETED = "COMPLETED";

    private final RefundMapper refundMapper;
    private final PaymentMapper paymentMapper;
    private final SettlementMapper settlementMapper;
    private final NotificationService notificationService;

    /**
     * 결제 한 건에 대한 환불을 접수하고 그 자리에서 바로 처리(모의 환불)한다.
     *
     * @param actingUserId 이 환불을 처리한 사용자(관리자 등). REFUND 테이블에는 별도 컬럼이 없어
     *                     아직 저장하지 않고 로그로만 남긴다 — 감사로그(AuditLog) 연동은 후속 과제.
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 대상 결제가 없을 때
     * @throws CommonException {@link ErrorCode#REFUND_TARGET_NOT_REFUNDABLE} 결제가 COMPLETED 상태가 아니거나,
     *         이미 정산에 포함된 결제일 때
     * @throws CommonException {@link ErrorCode#REFUND_ALREADY_PROCESSED} 이미 환불이 접수된 결제일 때
     */
    @Transactional
    public RefundResponse refund(Long paymentId, Long actingUserId, RefundRequest request) {
        // FOR UPDATE로 잠근다 — SettlementService.calculate()의 selectCompletedVendorFeePayments도
        // 같은 결제 행을 잠그기 때문에, 둘 중 하나가 끝날 때까지 나머지가 대기하게 된다(위 클래스
        // 문서 "동시성" 참고). selectById가 아니라 이 잠금 조회를 써야 경쟁 조건이 막힌다.
        PaymentRow payment = paymentMapper.selectByIdForUpdate(paymentId);
        if (payment == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!COMPLETED.equals(payment.getStatus())) {
            throw new CommonException(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE);
        }
        // 이미 정산(SETTLEMENT_ITEM)에 포함된 결제면 환불을 막는다 — 재계산 기능이 없어서
        // 지금 환불을 허용하면 이미 계산·확정된 정산 금액이 조용히 옛날 값으로 남는다(알려진 한계).
        if (settlementMapper.selectItemByPaymentId(paymentId) != null) {
            throw new CommonException(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE,
                    "이미 정산에 포함된 결제는 환불할 수 없습니다. 정산 담당자에게 문의해 주세요.");
        }

        LocalDateTime now = LocalDateTime.now();
        RefundRow row = new RefundRow();
        row.setPaymentId(paymentId);
        // reservationId는 DB에 저장되는 값이 아니라(REFUND 테이블에 컬럼 없음) 응답에만 실어
        // 보내는 편의 필드 — insert()는 이 값을 안 쓴다(RefundMapper.xml의 insert 참고).
        row.setReservationId(payment.getReservationId());
        row.setRefundReason(request.refundReason().name());
        row.setRequestedByDomain(request.requestedByDomain().name());
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

        notifyRefundCompleted(payment, row);

        return RefundResponse.from(row);
    }

    /**
     * 환불 완료를 알림함(IN_APP)에 남긴다. 이메일까지 보내려면 결제자 이메일 주소가 필요한데
     * 결제 도메인엔 없어(회원 도메인 users 테이블 소관, 도메인 경계상 직접 조회 안 함) — 이메일
     * 조회용 내부 계약이 생기면 EMAIL 채널을 추가할 예정(TODO).
     *
     * <p>문구에 "카드사 영업일 기준 반영" 안내가 들어가는데(CodeRabbit 리뷰 지적, PR #47),
     * 곧 진짜 PG(토스 결제취소 API) 연동될 거라 지금 문구가 미래 시점 기준으로는 맞음.
     *
     * <p>알림 저장이 실패해도 환불 자체는 이미 완료된 상태이므로 예외를 던져 환불 응답을
     * 실패로 되돌리지 않는다 — notifyReservationDomain과 같은 이유.
     */
    private void notifyRefundCompleted(PaymentRow payment, RefundRow refund) {
        try {
            notificationService.save(new SaveNotificationDto.Request(
                    payment.getPayerUserId(),
                    RecipientType.USER,
                    NotificationType.REFUND_COMPLETED,
                    "환불이 완료되었습니다",
                    "환불 금액 " + refund.getRefundAmount() + "원이 처리되었습니다. "
                            + "카드사에 따라 영업일 기준 3~5일 이내 반영됩니다.",
                    null,
                    List.of(DeliveryChannel.IN_APP),
                    null
            ));
        } catch (Exception e) {
            log.error("환불 완료 알림 저장 실패. refundId={}, paymentId={}",
                    refund.getRefundId(), refund.getPaymentId(), e);
        }
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
