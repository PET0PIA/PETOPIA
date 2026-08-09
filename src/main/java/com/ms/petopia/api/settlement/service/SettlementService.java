package com.ms.petopia.api.settlement.service;

import com.ms.petopia.api.commisionrate.service.CommissionRateService;
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
 * 쓴다 — PENDING 상태인 동안만 가능하고, CONFIRMED 이후는 "확정 이후 변경은 감사기록 필수"라는
 * 규칙 때문에 지원하지 않는다(재계산 정정 절차는 이번 스코프 밖).
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
     * @throws CommonException {@link ErrorCode#SETTLEMENT_ALREADY_EXISTS} 이미 계산된 정산이 있을 때
     */
    @Transactional
    public SettlementResponse calculate(Long fairId, Long businessId) {
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
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_RECALCULABLE} PENDING이 아니거나,
     *         재계산 중 동시에 확정돼버린 경우
     */
    @Transactional
    public SettlementResponse recalculate(Long settlementId) {
        SettlementRow row = settlementMapper.selectById(settlementId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
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
     * 지원하므로 CONFIRMED 이후 정정하려면 여전히 수동 처리가 필요하다(WBS 정산 규칙:
     * "확정 이후 변경은 감사기록 필수"이지만 그 정정 절차 자체는 이번 스코프 밖).
     *
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_FOUND} 존재하지 않는 정산일 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_CONFIRMABLE} PENDING이 아닐 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_RECALCULATION_REQUIRED} 재계산이
     *         필요한 상태(needs_recalculation)일 때 — 먼저 {@link #recalculate}를 호출해야 한다
     */
    public SettlementResponse confirm(Long settlementId, Long confirmedByUserId) {
        SettlementRow row = settlementMapper.selectById(settlementId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
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

        notifySettlementCompleted(row.getFairId(), row.getSettlementId());

        return SettlementResponse.from(row);
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
                    List.of(DeliveryChannel.IN_APP),
                    null
            ));
        } catch (Exception e) {
            log.error("정산 확정 알림 저장 실패. fairId={}, settlementId={}", fairId, settlementId, e);
        }
    }

    /** 행사·업체 조합으로 정산 단건 조회(참가업체 본인 조회용). */
    public SettlementResponse getByFairAndBusiness(Long fairId, Long businessId) {
        SettlementRow row = settlementMapper.selectByFairAndBusiness(fairId, businessId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        return SettlementResponse.from(row);
    }

    /** 행사 하나에 속한 정산 목록 조회(박람회관리자 조회용). */
    public List<SettlementResponse> getByFair(Long fairId) {
        return settlementMapper.selectByFairId(fairId).stream()
                .map(SettlementResponse::from)
                .toList();
    }
}
