package com.ms.petopia.api.fairsettlement.service;

import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.auth.domain.User;
import com.ms.petopia.api.auth.mapper.AuthMapper;
import com.ms.petopia.api.auth.service.MailService;
import com.ms.petopia.api.commisionrate.service.CommissionRateService;
import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.fairsettlement.dto.FairSettlementItemRow;
import com.ms.petopia.api.fairsettlement.dto.FairSettlementResponse;
import com.ms.petopia.api.fairsettlement.dto.FairSettlementRow;
import com.ms.petopia.api.fairsettlement.mapper.FairSettlementMapper;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.api.settlement.client.FairContractClient;
import com.ms.petopia.api.settlement.dto.FairCancellationStatus;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 행사별 최종정산(플랫폼 ↔ 행사) 계산·확정·조회 서비스(2026-08-22).
 *
 * <p>기존 {@code settlement.service.SettlementService}(업체별 정산, fair_id+business_id 단위)는
 * 그대로 두고 건드리지 않는다 - 나중에 필요해질 수 있어 백엔드에 남겨두기로 한 팀 결정이다.
 * 이 서비스는 그 대신 화면에 실제로 붙는 도메인으로, 업체 구분 없이 행사 하나당 정산 1건만
 * 가진다: 그 행사에 참가한 모든 업체의 완료된 참가비(VENDOR_FEE)를 합산해서 플랫폼이 행사
 * (주최측)에 정산해주는 개념이다. 계산·확정·재계산·되돌리기·행사취소가드·감사기록 등 세부
 * 규칙은 전부 기존 SettlementService와 동일한 패턴을 따른다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FairSettlementService {

    private static final String PENDING = "PENDING";
    private static final String COMPLETED = "COMPLETED";

    private final FairSettlementMapper fairSettlementMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final CommissionRateService commissionRateService;
    private final NotificationService notificationService;
    private final RecruitNoticeMapper recruitNoticeMapper;
    private final AuditLogService auditLogService;
    private final FairContractClient fairContractClient;
    private final FairAdminAccessGuard fairAdminAccessGuard;
    private final AuthMapper authMapper;
    private final MailService mailService;

    /**
     * 행사 하나의 최종정산을 계산해서 확정 전 상태(PENDING)로 만든다. 그 행사에 참가한 모든
     * 업체의 완료된 참가비를 합산하고, 그 중 환불완료된 금액은 차감한다.
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 관리자가 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_ALREADY_EXISTS} 이미 계산된 정산이 있을 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_FAIR_CANCELED} 취소된 행사일 때
     */
    @Transactional
    public FairSettlementResponse calculate(Long fairId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        assertFairNotCanceled(fairId);
        if (fairSettlementMapper.selectByFairId(fairId) != null) {
            throw new CommonException(ErrorCode.SETTLEMENT_ALREADY_EXISTS);
        }

        BigDecimal commissionRate = commissionRateService.resolveEffectiveRate(fairId);
        Aggregate aggregate = aggregate(fairId, commissionRate);

        LocalDateTime now = LocalDateTime.now();
        FairSettlementRow row = new FairSettlementRow();
        row.setFairId(fairId);
        row.setGrossAmount(aggregate.grossAmount());
        row.setRefundAmount(aggregate.refundAmount());
        row.setCommissionRate(commissionRate);
        row.setCommissionAmount(aggregate.commissionAmount());
        row.setNetAmount(aggregate.netAmount());
        row.setStatus(PENDING);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        try {
            fairSettlementMapper.insert(row);
            insertItems(row.getFairSettlementId(), aggregate.items());
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.SETTLEMENT_ALREADY_EXISTS);
        }

        return FairSettlementResponse.from(row);
    }

    /**
     * PENDING 정산을 현재 시점의 결제·환불 상태로 다시 집계한다. 재계산·확정은 SUPER_ADMIN
     * 전용 업무로 좁혔다(2026-08-22) - 그 행사 담당 EVENT_ADMIN이라도 호출할 수 없다.
     *
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_FOUND} 존재하지 않는 정산일 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} SUPER_ADMIN이 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_RECALCULABLE} PENDING이 아니거나,
     *         재계산 중 동시에 확정돼버린 경우
     */
    @Transactional
    public FairSettlementResponse recalculate(Long fairSettlementId) {
        FairSettlementRow row = fairSettlementMapper.selectById(fairSettlementId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        fairAdminAccessGuard.requireSuperAdmin();
        if (!PENDING.equals(row.getStatus())) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_RECALCULABLE);
        }

        // commission_rate는 calculate() 때 스냅샷해둔 값을 그대로 재사용한다.
        Aggregate aggregate = aggregate(row.getFairId(), row.getCommissionRate());

        LocalDateTime now = LocalDateTime.now();
        int updated = fairSettlementMapper.updateAggregates(fairSettlementId,
                aggregate.grossAmount(), aggregate.refundAmount(),
                aggregate.commissionAmount(), aggregate.netAmount(), now);
        if (updated == 0) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_RECALCULABLE);
        }

        fairSettlementMapper.deleteItemsByFairSettlementId(fairSettlementId);
        insertItems(fairSettlementId, aggregate.items());

        row.setGrossAmount(aggregate.grossAmount());
        row.setRefundAmount(aggregate.refundAmount());
        row.setCommissionAmount(aggregate.commissionAmount());
        row.setNetAmount(aggregate.netAmount());
        row.setUpdatedAt(now);
        return FairSettlementResponse.from(row);
    }

    private void insertItems(Long fairSettlementId, List<FairSettlementItemRow> items) {
        if (items.isEmpty()) {
            return;
        }
        for (FairSettlementItemRow item : items) {
            item.setFairSettlementId(fairSettlementId);
        }
        fairSettlementMapper.insertItems(items);
    }

    /**
     * 정산대상은 "결제완료된 참가비"만이고, 그 중 환불완료된 금액은 차감한다. 기존
     * SettlementService.aggregate()와 달리 businessId 필터 없이 그 행사의 모든 참가업체
     * 결제를 합산한다.
     *
     * <p>환불 조회는 결제 건마다 하나씩 부르지 않고 paymentId 목록으로 한 번에 배치 조회한다
     * (CodeRabbit 리뷰 지적, PR #222 - N+1은 selectCompletedVendorFeePaymentsByFair가 이미
     * 잡아둔 FOR UPDATE 락 유지 시간도 같이 늘렸다).
     */
    private Aggregate aggregate(Long fairId, BigDecimal commissionRate) {
        List<PaymentRow> payments = paymentMapper.selectCompletedVendorFeePaymentsByFair(fairId);

        List<Long> paymentIds = payments.stream().map(PaymentRow::getPaymentId).toList();
        Map<Long, RefundRow> refundsByPaymentId = paymentIds.isEmpty()
                ? Map.of()
                : refundMapper.selectByPaymentIds(paymentIds).stream()
                        .collect(Collectors.toMap(RefundRow::getPaymentId, row -> row));

        long grossAmount = 0L;
        long refundAmount = 0L;
        List<FairSettlementItemRow> items = new ArrayList<>();
        for (PaymentRow payment : payments) {
            grossAmount += payment.getAmount();

            RefundRow refund = refundsByPaymentId.get(payment.getPaymentId());
            long refunded = (refund != null && COMPLETED.equals(refund.getStatus()))
                    ? refund.getRefundAmount() : 0L;
            refundAmount += refunded;

            FairSettlementItemRow item = new FairSettlementItemRow();
            item.setPaymentId(payment.getPaymentId());
            item.setRefundId(refund == null ? null : refund.getRefundId());
            item.setAmountIncluded(payment.getAmount() - refunded);
            items.add(item);
        }

        long netBeforeCommission = grossAmount - refundAmount;
        long commissionAmount = commissionRate
                .multiply(BigDecimal.valueOf(netBeforeCommission))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long netAmount = netBeforeCommission - commissionAmount;

        return new Aggregate(grossAmount, refundAmount, commissionAmount, netAmount, items);
    }

    private record Aggregate(long grossAmount, long refundAmount, long commissionAmount, long netAmount,
                              List<FairSettlementItemRow> items) {
    }

    /**
     * 정산을 확정한다(SUPER_ADMIN 전용, 2026-08-22 — 담당 EVENT_ADMIN도 호출 불가하도록 좁힘).
     * 확정 이후 금액은 불변 - 정정하려면 {@link #reopen}으로 PENDING까지 되돌린 뒤 다시 거쳐야 한다.
     *
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_FOUND} 존재하지 않는 정산일 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} SUPER_ADMIN이 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_FAIR_CANCELED} 그 사이 행사가 취소됐을 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_CONFIRMABLE} PENDING이 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_RECALCULATION_REQUIRED} 재계산이 필요한 상태일 때
     */
    @Transactional
    public FairSettlementResponse confirm(Long fairSettlementId, Long confirmedByUserId) {
        FairSettlementRow row = fairSettlementMapper.selectById(fairSettlementId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        fairAdminAccessGuard.requireSuperAdmin();
        assertFairNotCanceled(row.getFairId());
        if (!PENDING.equals(row.getStatus())) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_CONFIRMABLE);
        }
        if (row.isNeedsRecalculation()) {
            throw new CommonException(ErrorCode.SETTLEMENT_RECALCULATION_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = fairSettlementMapper.confirm(fairSettlementId, confirmedByUserId, now, now);
        if (updated == 0) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_CONFIRMABLE);
        }

        row.setStatus("CONFIRMED");
        row.setConfirmedByUserId(confirmedByUserId);
        row.setConfirmedAt(now);

        auditLogService.record(
                confirmedByUserId,
                ActorType.ADMIN,
                "SUPER_ADMIN",
                ActionType.SETTLEMENT_CONFIRM,
                TargetType.FAIR_SETTLEMENT,
                fairSettlementId,
                Map.of("status", "PENDING"),
                Map.of("status", "CONFIRMED")
        );

        notifySettlementCompletedAfterCommit(row);

        return FairSettlementResponse.from(row);
    }

    /**
     * CONFIRMED 정산을 PENDING으로 되돌린다(SUPER_ADMIN 전용, 기존 settlement 도메인과 동일하게
     * confirm보다 더 신중해야 하는 결정이라 좁게 제한한다).
     *
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_FOUND} 존재하지 않는 정산일 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} SUPER_ADMIN이 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_REOPENABLE} CONFIRMED가 아니거나 경쟁 상황일 때
     */
    @Transactional
    public FairSettlementResponse reopen(Long fairSettlementId, Long actingUserId) {
        FairSettlementRow row = fairSettlementMapper.selectById(fairSettlementId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        fairAdminAccessGuard.requireSuperAdmin();
        if (!"CONFIRMED".equals(row.getStatus())) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_REOPENABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = fairSettlementMapper.reopen(fairSettlementId, now);
        if (updated == 0) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_REOPENABLE);
        }

        auditLogService.record(
                actingUserId,
                ActorType.ADMIN,
                "SUPER_ADMIN",
                ActionType.SETTLEMENT_REOPEN,
                TargetType.FAIR_SETTLEMENT,
                fairSettlementId,
                Map.of(
                        "status", "CONFIRMED",
                        "grossAmount", row.getGrossAmount(),
                        "refundAmount", row.getRefundAmount(),
                        "commissionAmount", row.getCommissionAmount(),
                        "netAmount", row.getNetAmount()
                ),
                Map.of("status", "PENDING")
        );

        row.setStatus(PENDING);
        row.setConfirmedAt(null);
        row.setConfirmedByUserId(null);
        row.setNeedsRecalculation(true);
        row.setUpdatedAt(now);

        return FairSettlementResponse.from(row);
    }

    /**
     * @throws CommonException {@link ErrorCode#SETTLEMENT_FAIR_CANCELED} 취소된 행사일 때
     */
    private void assertFairNotCanceled(Long fairId) {
        FairCancellationStatus status = fairContractClient.getCancellationStatus(fairId);
        if (status.canceled()) {
            throw new CommonException(ErrorCode.SETTLEMENT_FAIR_CANCELED);
        }
    }

    private void notifySettlementCompletedAfterCommit(FairSettlementRow row) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifySettlementCompleted(row);
            }
        });
    }

    private void notifySettlementCompleted(FairSettlementRow row) {
        Long fairId = row.getFairId();
        Long fairSettlementId = row.getFairSettlementId();
        try {
            Long adminUserId = recruitNoticeMapper.selectAdminUserIdByFairId(fairId);
            if (adminUserId != null) {
                notificationService.save(new SaveNotificationDto.Request(
                        adminUserId,
                        RecipientType.EVENT_ADMIN,
                        NotificationType.SETTLEMENT_COMPLETED,
                        "행사 정산이 확정되었습니다",
                        "행사 최종정산(ID: " + fairSettlementId + ")이 확정 처리되었습니다.",
                        null,
                        List.of(DeliveryChannel.IN_APP),
                        null
                ));
                sendSettlementCompletedEmail(adminUserId, row);
            }
        } catch (Exception e) {
            log.error("행사 정산 확정 알림 저장 실패. fairId={}, fairSettlementId={}", fairId, fairSettlementId, e);
        }

        try {
            notificationService.notifySuperAdmins(
                    NotificationType.SETTLEMENT_COMPLETED,
                    "행사 정산이 확정되었습니다",
                    "행사 최종정산(ID: " + fairSettlementId + ", fairId=" + fairId + ")이 확정 처리되었습니다."
            );
        } catch (Exception e) {
            log.error("행사 정산 확정 SUPER_ADMIN 알림 저장 실패. fairId={}, fairSettlementId={}", fairId, fairSettlementId, e);
        }
    }

    private void sendSettlementCompletedEmail(Long adminUserId, FairSettlementRow row) {
        try {
            User user = authMapper.selectUserById(adminUserId);
            if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                return;
            }
            mailService.sendSettlementCompletedEmail(user.getEmail(), "행사 최종정산", row.getFairSettlementId(),
                    row.getGrossAmount(), row.getRefundAmount(), row.getCommissionAmount(), row.getNetAmount());
        } catch (Exception e) {
            log.error("행사 정산 확정 이메일 발송 실패. fairId={}, fairSettlementId={}",
                    row.getFairId(), row.getFairSettlementId(), e);
        }
    }

    /**
     * 행사 하나의 최종정산 단건 조회(EVENT_ADMIN/SUPER_ADMIN). 계산된 적이 없으면 null을
     * 반환한다(에러로 취급하지 않는다 - 화면이 "아직 계산 안 됨" 상태를 그대로 보여줄 수 있게).
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 관리자가 아닐 때
     */
    public FairSettlementResponse getByFairId(Long fairId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        FairSettlementRow row = fairSettlementMapper.selectByFairId(fairId);
        return row == null ? null : FairSettlementResponse.from(row);
    }
}
