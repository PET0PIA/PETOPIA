package com.ms.petopia.api.refund.service;

import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.service.MailService;
import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.api.fairsettlement.mapper.FairSettlementMapper;
import com.ms.petopia.api.settlement.mapper.SettlementMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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
 * <p><b>정산과의 관계</b>: 결제가 이미 CONFIRMED 정산에 포함돼 있으면 환불을 거부한다(확정 이후
 * 금액은 불변이라는 규칙, PR #47 CodeRabbit 리뷰 지적). PENDING 정산에 포함된 결제는 환불을
 * 허용하는 대신 그 정산에 "재계산 필요"(needs_recalculation) 표시를 원자적으로 남긴다 —
 * {@code SettlementService.confirm}은 이 표시가 있으면 확정을 거부하고, 정산 담당자가
 * {@code recalculate}를 호출해서 최신 완료/환불 상태를 반영한 금액으로 다시 계산한 뒤에야
 * 확정할 수 있다(PR #54 CodeRabbit 리뷰 지적 — 표시 없이 상태만 읽던 예전 방식은 환불과
 * confirm이 동시에 일어나면 옛날 금액이 그대로 확정돼버리는 경쟁 조건이 있었다).
 *
 * <p><b>동시성</b>: {@link #refund}와 {@code SettlementService.calculate}/{@code recalculate}가
 * 동시에 같은 결제를 건드리면(정산 집계가 이 결제를 포함시키는 도중 환불이 끼어드는 경우) 정산
 * 금액이 환불 반영 전 값으로 굳을 수 있었다(CodeRabbit 리뷰 지적, PR #47). 그래서 셋 다 같은
 * {@code PAYMENT} 행을 {@code FOR UPDATE}로 잠그고 트랜잭션 안에서 처리하도록 맞춰서,
 * 어느 쪽이 먼저 시작하든 나머지가 끝날 때까지 기다렸다가 최신 상태를 보고 진행한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefundService {

    private static final String COMPLETED = "COMPLETED";

    private final RefundMapper refundMapper;
    private final PaymentMapper paymentMapper;
    private final SettlementMapper settlementMapper;
    private final FairSettlementMapper fairSettlementMapper;
    private final NotificationService notificationService;
    private final FairAdminAccessGuard fairAdminAccessGuard;
    private final RecruitNoticeMapper recruitNoticeMapper;
    private final AuthMapper authMapper;
    private final MailService mailService;

    /**
     * 환불 요청자가 결제 소유자 또는 그 행사 담당 EVENT_ADMIN/SUPER_ADMIN인지 확인한다
     * (HTTP 진입점 전용). {@link #refund}/{@link #refundOrReuse} 자체엔 이 검증을 넣지 않는다 —
     * 채린님(참가업체)·승훈님(행사) 도메인이 시스템 명의({@code PaymentService.SYSTEM_ACTOR_USER_ID})로
     * SecurityContext 없이 직접 빈 주입 호출하는 내부 경로가 있어서다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 대상 결제가 없을 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 소유자도, 그 행사 담당 관리자도 아닐 때
     */
    public void assertRequesterAuthorized(Long paymentId, Long actingUserId) {
        PaymentRow payment = paymentMapper.selectById(paymentId);
        if (payment == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!actingUserId.equals(payment.getPayerUserId())) {
            fairAdminAccessGuard.checkAssigned(payment.getFairId());
        }
    }

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
        return create(lockRefundablePayment(paymentId), actingUserId, request);
    }

    /**
     * 환불을 만들되, 이미 환불된 결제면 예외 대신 기존 환불을 그대로 반환한다.
     * 결제 행을 잠근 뒤에 판단하므로 "재사용 또는 신규 생성"이 원자적이다.
     *
     * <p><b>왜 필요한가</b>: 호출자가 {@link #findByPaymentId}로 먼저 확인하고 {@link #refund}를
     * 부르는 방식은 두 호출 사이에 창이 있다. 그 사이 행사 취소 일괄환불
     * ({@code FairCancelRefundOrchestrationService})이 같은 결제의 환불을 커밋하면 INSERT가
     * UK_REFUND_PAYMENT에 걸려 {@link ErrorCode#REFUND_ALREADY_PROCESSED}가 되고, 호출자
     * 트랜잭션(예: 예약 취소)까지 통째로 롤백된다 — 사용자 입장에선 "환불은 이미 됐는데 예약은
     * 취소가 안 되는" 상태다. 확인과 생성을 같은 잠금 구간 안으로 넣어서 그 창을 없앤다.
     *
     * <p>{@link #refund}와 달리 REFUND_ALREADY_PROCESSED를 던지지 않는다. 반대로 {@link #refund}는
     * 의미를 그대로 남겨둔다 — 행사 취소 일괄환불이 이 에러코드를 재시도 불가(terminal) 판정에
     * 쓰고 있어서({@code FairCancelRefundOrchestrationService}의 TERMINAL_ERROR_CODES), 여기서
     * 재사용으로 바꾸면 그쪽 작업행 상태 전이(FAILED → COMPLETED)까지 함께 달라진다.
     *
     * @throws CommonException {@link ErrorCode#PAYMENT_NOT_FOUND} 대상 결제가 없을 때
     * @throws CommonException {@link ErrorCode#REFUND_TARGET_NOT_REFUNDABLE} 결제가 COMPLETED가
     *         아니거나, 이미 확정된 정산에 포함된 결제일 때
     */
    @Transactional
    public RefundResponse refundOrReuse(Long paymentId, Long actingUserId, RefundRequest request) {
        PaymentRow payment = paymentMapper.selectByIdForUpdate(paymentId);
        if (payment == null) {
            throw new CommonException(ErrorCode.PAYMENT_NOT_FOUND);
        }

        // 결제 행을 잠근 뒤 잠금 읽기로 확인한다. 비잠금 읽기는 REPEATABLE READ 스냅샷 때문에
        // 방금 커밋된 환불을 못 볼 수 있다(RefundMapper.selectByPaymentIdForUpdate 참고).
        RefundRow existing = refundMapper.selectByPaymentIdForUpdate(paymentId);
        if (existing != null) {
            // 조인 없이 읽어와서 reservationId가 비어 있다 — 이미 잠가서 들고 있는 결제 행에서 채운다.
            existing.setReservationId(payment.getReservationId());
            log.info("이미 환불된 결제라 기존 환불을 재사용한다. paymentId={}, refundId={}, actingUserId={}",
                    paymentId, existing.getRefundId(), actingUserId);
            return RefundResponse.from(existing);
        }

        if (!COMPLETED.equals(payment.getStatus())) {
            throw new CommonException(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE);
        }
        return create(payment, actingUserId, request);
    }

    /** 환불 대상 결제를 잠그고 환불 가능한 상태인지 확인한다. */
    private PaymentRow lockRefundablePayment(Long paymentId) {
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
        return payment;
    }

    /** 잠긴 결제 행을 받아 정산 표시를 남기고 환불 한 건을 만든다. 호출 전에 결제 잠금이 필요하다. */
    private RefundResponse create(PaymentRow payment, Long actingUserId, RefundRequest request) {
        Long paymentId = payment.getPaymentId();
        // 이 결제가 정산에 포함돼 있으면(PENDING일 때만) "재계산 필요" 표시를 원자적으로 남기고
        // 환불을 허용한다 — 정산 담당자가 SettlementService.recalculate로 나중에 금액을 바로잡는
        // 걸 전제로 한다. markNeedsRecalculation의 WHERE status='PENDING' 조건이
        // SettlementService.confirm()의 원자적 확정 UPDATE와 같은 SETTLEMENT 행을 두고 경쟁하므로,
        // 둘 중 먼저 커밋한 쪽이 이긴다(CodeRabbit 리뷰 지적, PR #54 — 예전엔 상태만 읽고 끝나서
        // "PENDING 확인 직후 confirm이 먼저 끝나버리는" 경쟁을 못 막았다). 이미 CONFIRMED로
        // 넘어간 정산이면 이 UPDATE가 0행이라 환불을 거부한다.
        Long settlementId = settlementMapper.selectSettlementIdByPaymentId(paymentId);
        if (settlementId != null && settlementMapper.markNeedsRecalculation(settlementId) == 0) {
            throw new CommonException(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE,
                    "이미 확정된 정산에 포함된 결제는 환불할 수 없습니다. 정산 담당자에게 문의해 주세요.");
        }
        // 행사별 최종정산(2026-08-22)도 같은 결제를 포함하고 있을 수 있어 똑같이 확인한다 -
        // 위 업체별 정산과는 완전히 별개 테이블(fair_settlement_item)이라 둘 다 체크해야 한다.
        Long fairSettlementId = fairSettlementMapper.selectFairSettlementIdByPaymentId(paymentId);
        if (fairSettlementId != null && fairSettlementMapper.markNeedsRecalculation(fairSettlementId) == 0) {
            throw new CommonException(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE,
                    "이미 확정된 정산에 포함된 결제는 환불할 수 없습니다. 정산 담당자에게 문의해 주세요.");
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
     * 환불 완료를 알림함(IN_APP)과 이메일로 남긴다. recipientContact를 null로 넘기면
     * NotificationService가 userId로 회원 이메일을 자동 조회해서 보낸다.
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
        Long payerUserId = payment.getPayerUserId();
        Long refundId = refund.getRefundId();
        Long paymentId = refund.getPaymentId();
        long refundAmount = refund.getRefundAmount();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    User user = authMapper.selectUserById(payerUserId);
                    if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
                        mailService.sendRefundCompletedEmail(user.getEmail(), refundAmount);
                    }
                } catch (Exception e) {
                    log.error("환불 완료 이메일 발송 실패. refundId={}, paymentId={}", refundId, paymentId, e);
                }
            }
        });
        notifyRefundCompletedToAdmins(payment, refund);
    }

    /** 환불 발생을 행사 담당 EVENT_ADMIN에게 알린다. 실패해도 환불 처리에는 영향 없음. */
    private void notifyRefundCompletedToAdmins(PaymentRow payment, RefundRow refund) {
        String payerNickname = resolvePayerNickname(payment.getPayerUserId());
        String body = payerNickname + "님의 환불 " + refund.getRefundAmount() + "원이 처리되었습니다.";

        try {
            Long adminUserId = payment.getFairId() == null
                    ? null : recruitNoticeMapper.selectAdminUserIdByFairId(payment.getFairId());
            if (adminUserId != null) {
                notificationService.save(new SaveNotificationDto.Request(
                        adminUserId,
                        RecipientType.EVENT_ADMIN,
                        NotificationType.REFUND_COMPLETED,
                        "환불이 접수되었습니다",
                        body,
                        null,
                        List.of(DeliveryChannel.IN_APP),
                        null
                ));
            }
        } catch (Exception e) {
            log.error("환불 완료 EVENT_ADMIN 알림 저장 실패. refundId={}, paymentId={}, fairId={}",
                    refund.getRefundId(), refund.getPaymentId(), payment.getFairId(), e);
        }
    }

    private String resolvePayerNickname(Long payerUserId) {
        try {
            User user = authMapper.selectUserById(payerUserId);
            return user != null && user.getNickname() != null ? user.getNickname() : "알 수 없는 사용자";
        } catch (Exception e) {
            log.warn("결제자 닉네임 조회 실패. payerUserId={}", payerUserId, e);
            return "알 수 없는 사용자";
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
