package com.ms.petopia.api.settlement.service;

import com.ms.petopia.api.audit.model.ActionType;
import com.ms.petopia.api.audit.model.ActorType;
import com.ms.petopia.api.audit.model.TargetType;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.commisionrate.service.CommissionRateService;
import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.dto.FairRevenueSummaryRow;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.recruitnotice.mapper.RecruitNoticeMapper;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.api.settlement.client.FairContractClient;
import com.ms.petopia.api.settlement.dto.FairCancellationStatus;
import com.ms.petopia.api.settlement.dto.FairRevenueSummaryResponse;
import com.ms.petopia.api.settlement.dto.SettlementItemRow;
import com.ms.petopia.api.settlement.dto.SettlementResponse;
import com.ms.petopia.api.settlement.dto.SettlementRow;
import com.ms.petopia.api.settlement.mapper.SettlementMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 정산 계산·확정·조회 서비스.
 *
 * <p>수수료율은 {@link CommissionRateService}가 전역 기본값 + 행사별 override 2단계로
 * 관리한다(WBS 5.1~5.5). {@link #calculate}가 정산 생성 시점에 "지금 적용될" 요율을 조회해서
 * {@code settlement.commission_rate}에 스냅샷으로 저장하고, 이후 요율이 바뀌어도 그 정산은
 * 영향받지 않는다 — {@link #recalculate}도 새로 조회하지 않고 이 스냅샷을 그대로 재사용한다.
 *
 * <p>{@link #calculate}로 한 번 만들어진 정산은 같은 행사·업체 조합으로 다시 계산 요청하면
 * {@code UK_SETTLEMENT_FAIR_BUSINESS} 때문에 {@link ErrorCode#SETTLEMENT_ALREADY_EXISTS}가
 * 난다. 계산 이후 결제가 새로 완료되거나 환불이 들어와서 금액을 갱신해야 하면 {@link #recalculate}를
 * 쓴다 — PENDING 상태인 동안만 가능하다. CONFIRMED 이후 정정이 필요하면 {@link #reopen}으로
 * PENDING까지 되돌린 뒤 recalculate·confirm을 다시 거친다("확정 이후 변경은 감사기록 필수"
 * 규칙은 reopen()이 감사로그를 남기는 것으로 충족한다).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private static final String PENDING = "PENDING";
    private static final String COMPLETED = "COMPLETED";

    private final SettlementMapper settlementMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;
    private final CommissionRateService commissionRateService;
    private final NotificationService notificationService;
    private final RecruitNoticeMapper recruitNoticeMapper;
    private final AuditLogService auditLogService;
    private final FairContractClient fairContractClient;
    private final FairAdminAccessGuard fairAdminAccessGuard;

    /**
     * 특정 행사·업체의 정산을 계산해서 확정 전 상태(PENDING)로 만든다.
     *
     * <p>정산대상은 "결제완료된 참가비"만이고, 그 중 환불완료된 금액은 차감한다
     * (예약금·행사개설비는 운영매출 조회대상일 뿐 참가업체 정산대상 아님).
     * 각 결제·환불 건은 SETTLEMENT_ITEM에 감사근거로 같이 남긴다.
     *
     * <p>원래는 "행사 종료 후" 자동 집계가 목표지만(WBS 4.2), 이번 스코프에는 트리거할
     * 스케줄러/이벤트 인프라가 없어서 관리자가 수동으로 호출하는 API로 대신한다 — 자동화는
     * 후속 과제.
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 관리자가 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_ALREADY_EXISTS} 이미 계산된 정산이 있을 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_FAIR_CANCELED} 취소된 행사일 때
     */
    @Transactional
    public SettlementResponse calculate(Long fairId, Long businessId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        assertFairNotCanceled(fairId);
        if (settlementMapper.selectByFairAndBusiness(fairId, businessId) != null) {
            throw new CommonException(ErrorCode.SETTLEMENT_ALREADY_EXISTS);
        }

        // 정산 생성 시점에 "지금 적용될" 요율을 딱 한 번 조회해서 이 정산에 스냅샷으로 고정한다
        // (행사별 override가 있으면 그게 우선, 없으면 전역 기본값). 이후 요율이 바뀌어도
        // 이미 만들어진 정산에는 영향 없다 — recalculate()도 이 값을 새로 조회하지 않고 재사용.
        BigDecimal commissionRate = commissionRateService.resolveEffectiveRate(fairId);
        Aggregate aggregate = aggregate(fairId, businessId, commissionRate);

        LocalDateTime now = LocalDateTime.now();
        SettlementRow row = new SettlementRow();
        row.setFairId(fairId);
        row.setBusinessId(businessId);
        row.setGrossAmount(aggregate.grossAmount());
        row.setRefundAmount(aggregate.refundAmount());
        row.setCommissionRate(commissionRate);
        row.setCommissionAmount(aggregate.commissionAmount());
        row.setNetAmount(aggregate.netAmount());
        row.setStatus(PENDING);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        try {
            settlementMapper.insert(row);
            insertItems(row.getSettlementId(), aggregate.items());
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.SETTLEMENT_ALREADY_EXISTS);
        }

        return SettlementResponse.from(row);
    }

    /**
     * PENDING 정산을 현재 시점의 결제·환불 상태로 다시 집계한다. 계산 당시엔 없던 결제가 새로
     * 완료되거나, 계산 이후 환불이 들어온 경우를 반영하는 용도다.
     *
     * <p>기존 SETTLEMENT_ITEM은 전부 지우고 최신 내역으로 다시 채운다 — {@code calculate}와
     * 달리 여기서는 재계산이 몇 번이든 반복될 수 있어 upsert 대신 delete-then-insert가 더 단순하다.
     *
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_FOUND} 존재하지 않는 정산일 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 관리자가 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_RECALCULABLE} PENDING이 아니거나,
     *         재계산 중 동시에 확정돼버린 경우
     */
    @Transactional
    public SettlementResponse recalculate(Long settlementId) {
        SettlementRow row = settlementMapper.selectById(settlementId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        fairAdminAccessGuard.checkAssigned(row.getFairId());
        if (!PENDING.equals(row.getStatus())) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_RECALCULABLE);
        }

        // commission_rate는 calculate() 때 스냅샷해둔 값을 그대로 재사용한다 — 새로 조회하면
        // 그 사이 바뀐 요율이 이미 PENDING인 정산에 몰래 끼어들게 된다(클래스 문서 참고).
        Aggregate aggregate = aggregate(row.getFairId(), row.getBusinessId(), row.getCommissionRate());

        LocalDateTime now = LocalDateTime.now();
        int updated = settlementMapper.updateAggregates(settlementId,
                aggregate.grossAmount(), aggregate.refundAmount(),
                aggregate.commissionAmount(), aggregate.netAmount(), now);
        if (updated == 0) {
            // selectById 이후 이 UPDATE 사이에 다른 요청이 먼저 확정한 경우(동시성 방어, confirm과 같은 패턴)
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_RECALCULABLE);
        }

        settlementMapper.deleteItemsBySettlementId(settlementId);
        insertItems(settlementId, aggregate.items());

        row.setGrossAmount(aggregate.grossAmount());
        row.setRefundAmount(aggregate.refundAmount());
        row.setCommissionAmount(aggregate.commissionAmount());
        row.setNetAmount(aggregate.netAmount());
        row.setUpdatedAt(now);
        return SettlementResponse.from(row);
    }

    /** items가 비어있지 않을 때만 settlementId를 채워 일괄 저장한다(calculate·recalculate 공용). */
    private void insertItems(Long settlementId, List<SettlementItemRow> items) {
        if (items.isEmpty()) {
            return;
        }
        for (SettlementItemRow item : items) {
            item.setSettlementId(settlementId);
        }
        // UK_SETTLEMENT_ITEM_PAYMENT(payment_id 전역 유니크) 위반 시에도 여기서 잡는다 —
        // 같은 결제가 다른 정산에 이미 포함돼 있으면(정상 흐름에선 fair+business 선점 체크로
        // 막히지만, 방어적으로) 500 대신 의미 있는 409로 응답한다.
        settlementMapper.insertItems(items);
    }

    /**
     * 정산대상은 "결제완료된 참가비"만이고, 그 중 환불완료된 금액은 차감한다
     * (예약금·행사개설비는 운영매출 조회대상일 뿐 참가업체 정산대상 아님).
     * calculate·recalculate가 공유하는 집계 로직. commissionRate는 호출자가 정한다 —
     * calculate()는 새로 조회한 값을, recalculate()는 기존 스냅샷을 넘긴다.
     */
    private Aggregate aggregate(Long fairId, Long businessId, BigDecimal commissionRate) {
        List<PaymentRow> payments = paymentMapper.selectCompletedVendorFeePayments(fairId, businessId);

        long grossAmount = 0L;
        long refundAmount = 0L;
        List<SettlementItemRow> items = new ArrayList<>();
        for (PaymentRow payment : payments) {
            grossAmount += payment.getAmount();

            RefundRow refund = refundMapper.selectByPaymentId(payment.getPaymentId());
            long refunded = (refund != null && COMPLETED.equals(refund.getStatus()))
                    ? refund.getRefundAmount() : 0L;
            refundAmount += refunded;

            SettlementItemRow item = new SettlementItemRow();
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
                              List<SettlementItemRow> items) {
    }

    /**
     * 정산을 확정한다(SUPER_ADMIN). 확정 이후 금액은 불변 — {@link #recalculate}도 PENDING까지만
     * 지원한다. CONFIRMED 이후 정정하려면 {@link #reopen}으로 PENDING까지 되돌린 뒤 다시 거쳐야
     * 한다(WBS 정산 규칙 "확정 이후 변경은 감사기록 필수"는 reopen()의 감사로그로 충족).
     *
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_FOUND} 존재하지 않는 정산일 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 관리자가 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_FAIR_CANCELED} 그 사이 행사가 취소됐을 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_CONFIRMABLE} PENDING이 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_RECALCULATION_REQUIRED} 재계산이
     *         필요한 상태(needs_recalculation)일 때 — 먼저 {@link #recalculate}를 호출해야 한다
     */
    @Transactional
    public SettlementResponse confirm(Long settlementId, Long confirmedByUserId) {
        SettlementRow row = settlementMapper.selectById(settlementId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        // 다른 행사 담당 EVENT_ADMIN이 남의 행사 정산을 확정하지 못하도록 먼저 확인한다
        // (CodeRabbit 리뷰 지적).
        fairAdminAccessGuard.checkAssigned(row.getFairId());
        assertFairNotCanceled(row.getFairId());
        if (!PENDING.equals(row.getStatus())) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_CONFIRMABLE);
        }
        if (row.isNeedsRecalculation()) {
            // 환불로 인해 재계산이 필요하다고 표시된 정산 — recalculate 없이 그냥 확정하면
            // 옛날(환불 반영 전) 금액으로 굳어버린다(CodeRabbit 리뷰 지적, PR #54)
            throw new CommonException(ErrorCode.SETTLEMENT_RECALCULATION_REQUIRED);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = settlementMapper.confirm(settlementId, confirmedByUserId, now, now);
        if (updated == 0) {
            // selectById 이후 이 UPDATE 사이에 동시에 다른 요청이 먼저 확정했거나, 환불이 먼저
            // needs_recalculation을 세워버린 경우(둘 다 동시성 방어, 뒤쪽은 PR #54 지적사항)
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
                TargetType.SETTLEMENT,
                settlementId,
                Map.of("status", "PENDING"),
                Map.of("status", "CONFIRMED")
        );

        notifySettlementCompleted(row.getFairId(), row.getSettlementId());

        return SettlementResponse.from(row);
    }

    /**
     * CONFIRMED 정산을 PENDING으로 되돌린다(SUPER_ADMIN 전용). 확정 후 오류가 발견되면 이걸로
     * 되돌린 뒤 {@link #recalculate}로 최신 금액을 반영하고 {@link #confirm}으로 다시 확정한다 —
     * 이 메서드 자체는 상태를 되돌리고 감사기록을 남기는 것만 담당하고, 금액 재계산은 기존
     * recalculate()를 그대로 재사용한다(클래스 문서 "재계산 정정 절차는 이번 스코프 밖" 항목 해소).
     *
     * <p>되돌리는 동시에 {@code needs_recalculation}을 TRUE로 세운다 — 이게 없으면 되돌린 직후
     * recalculate 없이 바로 confirm()을 다시 불러도 그냥 통과돼버려서(CONFIRMED 시점엔 이 값이
     * 이미 FALSE였으므로), 정정하려던 옛날 금액 그대로 재확정되는 구멍이 생긴다(CodeRabbit 리뷰
     * 지적, PR #181). confirm()의 기존 needsRecalculation 체크가 이 값 덕분에 "reopen 이후엔
     * 반드시 recalculate부터"를 강제한다.
     *
     * <p>{@link #confirm}은 그 행사 담당 EVENT_ADMIN도 할 수 있지만, 되돌리기는
     * {@link FairAdminAccessGuard#requireSuperAdmin}으로 더 좁게 제한한다 — 이미 확정되어
     * 지급 근거가 됐을 수 있는 정산을 되돌리는 결정은 확정보다 더 신중해야 해서다.
     *
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_FOUND} 존재하지 않는 정산일 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} SUPER_ADMIN이 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_REOPENABLE} CONFIRMED가 아니거나,
     *         되돌리는 사이 다른 요청이 먼저 처리해버린 경우(동시성 방어)
     */
    @Transactional
    public SettlementResponse reopen(Long settlementId, Long actingUserId) {
        SettlementRow row = settlementMapper.selectById(settlementId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        fairAdminAccessGuard.requireSuperAdmin();
        if (!"CONFIRMED".equals(row.getStatus())) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_REOPENABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = settlementMapper.reopen(settlementId, now);
        if (updated == 0) {
            // selectById 이후 이 UPDATE 사이에 다른 요청이 먼저 처리한 경우(동시성 방어,
            // confirm/updateAggregates와 같은 패턴).
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_REOPENABLE);
        }

        // 되돌리기 직전 확정 상태의 금액·확정자를 스냅샷으로 남긴다 — 이 기능 자체가
        // "확정 이후 정정"을 위한 것이라 무엇이 어떻게 바뀌었는지가 감사기록의 핵심이다.
        auditLogService.record(
                actingUserId,
                ActorType.ADMIN,
                "SUPER_ADMIN",
                ActionType.SETTLEMENT_REOPEN,
                TargetType.SETTLEMENT,
                settlementId,
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

        return SettlementResponse.from(row);
    }

    /**
     * 정산 계산·확정 직전에 행사가 취소되지 않았는지 확인한다. {@link #recalculate}는
     * 의도적으로 이 체크를 하지 않는다 — 취소된 행사라도 이미 계산된 정산은 환불 반영을
     * 위해 재계산 가능해야 하기 때문이다.
     *
     * @throws CommonException {@link ErrorCode#SETTLEMENT_FAIR_CANCELED} 취소된 행사일 때
     */
    private void assertFairNotCanceled(Long fairId) {
        FairCancellationStatus status = fairContractClient.getCancellationStatus(fairId);
        if (status.canceled()) {
            throw new CommonException(ErrorCode.SETTLEMENT_FAIR_CANCELED);
        }
    }

    private void notifySettlementCompleted(Long fairId, Long settlementId) {
        try {
            Long adminUserId = recruitNoticeMapper.selectAdminUserIdByFairId(fairId);
            if (adminUserId == null) {
                return;
            }
            notificationService.save(new SaveNotificationDto.Request(
                    adminUserId,
                    RecipientType.EVENT_ADMIN,
                    NotificationType.SETTLEMENT_COMPLETED,
                    "정산이 확정되었습니다",
                    "행사 정산(ID: " + settlementId + ")이 확정 처리되었습니다.",
                    null,
                    List.of(DeliveryChannel.IN_APP, DeliveryChannel.EMAIL),
                    null
            ));
        } catch (Exception e) {
            log.error("정산 확정 알림 저장 실패. fairId={}, settlementId={}", fairId, settlementId, e);
        }
    }

    /**
     * 행사·업체 조합으로 정산 단건 조회(EVENT_ADMIN/SUPER_ADMIN — SettlementController 참고).
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 관리자가 아닐 때
     */
    public SettlementResponse getByFairAndBusiness(Long fairId, Long businessId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        SettlementRow row = settlementMapper.selectByFairAndBusiness(fairId, businessId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        return SettlementResponse.from(row);
    }

    /**
     * 행사 하나에 속한 정산 목록 조회(박람회관리자 조회용). {@link SettlementExportService}가
     * 엑셀 다운로드에서도 재사용한다 — 둘 다 HTTP 요청 경로(SettlementController)로만 들어와서
     * 여기 가드를 넣어도 SecurityContext 없는 내부 호출과 충돌하지 않는다.
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 관리자가 아닐 때
     */
    public List<SettlementResponse> getByFair(Long fairId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        return settlementMapper.selectByFairId(fairId).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    /**
     * fairId·businessId 둘 다 선택적인 통합검색(SUPER_ADMIN 전용 정산 화면, 2026-08-21).
     * 최소 하나는 채워야 한다 - 둘 다 비었으면 정산 테이블 전체를 반환하게 돼서 막는다.
     * fairId가 있으면 그 행사 담당자인지 확인하고(SUPER_ADMIN은 항상 통과), fairId 없이
     * businessId만으로 여러 행사에 걸친 결과를 묶어 보는 건 SUPER_ADMIN만 허용한다
     * ({@link FairAdminAccessGuard#requireSuperAdmin} 그 용도로 이미 존재).
     *
     * @throws CommonException {@link ErrorCode#INVALID_INPUT_VALUE} fairId·businessId 둘 다 없을 때
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 접근 권한이 없을 때
     */
    public List<SettlementResponse> getByFilter(Long fairId, Long businessId) {
        if (fairId == null && businessId == null) {
            throw new CommonException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (fairId != null) {
            fairAdminAccessGuard.checkAssigned(fairId);
        } else {
            fairAdminAccessGuard.requireSuperAdmin();
        }
        return settlementMapper.selectByFilter(fairId, businessId).stream()
                .map(SettlementResponse::from)
                .toList();
    }

    /**
     * 행사별 매출 요약(SUPER_ADMIN 정산·수수료 화면 전용, WBS 5.6). 기존 {@link #calculate}와
     * 달리 저장하지 않는다 - 매번 결제/환불 데이터를 그대로 다시 집계해서 보여주는 조회 전용
     * 화면이라, "확정" 개념도 재계산도 없다(단순 재조회가 곧 재계산). 모든 행사를 대상으로 하는
     * 전체 현황판이라 개별 행사 담당자 확인(FairAdminAccessGuard)은 하지 않고, SUPER_ADMIN
     * 전용 접근은 SecurityConfig가 URL 단위로 막는다.
     */
    public List<FairRevenueSummaryResponse> getFairRevenueSummaries() {
        return paymentMapper.selectFairRevenueSummary().stream()
                .map(this::toRevenueSummaryResponse)
                .toList();
    }

    /**
     * 행사 하나의 매출 요약(EVENT_ADMIN 담당 행사 정산 화면용, 2026-08-22). 위 전체 목록판과
     * 달리 그 행사 담당자인지 확인한다(FairAdminAccessGuard) - 남의 행사 매출까지 보이면 안
     * 되므로, SecurityConfig의 URL 단위 SUPER_ADMIN 전용 제한과는 별도 경로로 둔다.
     *
     * @throws CommonException {@link ErrorCode#ACCESS_DENIED} 그 행사 담당 관리자가 아닐 때
     * @throws CommonException {@link ErrorCode#FAIR_NOT_FOUND} 존재하지 않는 행사일 때
     */
    public FairRevenueSummaryResponse getFairRevenueSummary(Long fairId) {
        fairAdminAccessGuard.checkAssigned(fairId);
        FairRevenueSummaryRow row = paymentMapper.selectFairRevenueSummaryByFairId(fairId);
        if (row == null) {
            throw new CommonException(ErrorCode.FAIR_NOT_FOUND);
        }
        return toRevenueSummaryResponse(row);
    }

    private FairRevenueSummaryResponse toRevenueSummaryResponse(FairRevenueSummaryRow row) {
        long grossAmount = row.getTicketAmount() + row.getVendorFeeAmount();
        BigDecimal commissionRate = commissionRateService.resolveEffectiveRate(row.getFairId());
        long platformAmount = commissionRate
                .multiply(BigDecimal.valueOf(grossAmount))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long businessAmount = grossAmount - platformAmount;

        return new FairRevenueSummaryResponse(
                row.getFairId(),
                row.getFairName(),
                row.getTicketAmount(),
                row.getVendorFeeAmount(),
                grossAmount,
                commissionRate,
                platformAmount,
                businessAmount
        );
    }
}
