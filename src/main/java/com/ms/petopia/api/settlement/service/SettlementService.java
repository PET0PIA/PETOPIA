package com.ms.petopia.api.settlement.service;

import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.api.settlement.dto.SettlementItemRow;
import com.ms.petopia.api.settlement.dto.SettlementResponse;
import com.ms.petopia.api.settlement.dto.SettlementRow;
import com.ms.petopia.api.settlement.mapper.SettlementMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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
 * <p>수수료율은 이번 스코프에서 COMMISSION_RATE 테이블·API 대신 상수로 고정한다
 * (WBS 5.1~5.5, [[project_petopia_refund_settlement_scope]] 참고). 나중에 전역·행사별
 * override를 지원하는 CommissionRate 조회로 교체될 자리다.
 *
 * <p><b>알려진 한계</b>: {@link #calculate}로 한 번 만들어진 정산(0원짜리 포함)은 재계산할 수
 * 없다(CodeRabbit 리뷰 지적, PR #47) — {@code UK_SETTLEMENT_FAIR_BUSINESS} 때문에 같은
 * 행사·업체 조합으로 다시 계산하면 무조건 {@link ErrorCode#SETTLEMENT_ALREADY_EXISTS}가 난다.
 * 계산 이후 결제가 새로 완료되거나 환불이 들어와도 반영 안 됨 — 완전한 재계산/정정 절차는
 * 무거워서 이번 스코프 밖으로 미뤘고, 대신 {@code RefundService}에서 "이미 정산에 포함된 결제는
 * 환불 자체를 거부"하는 최소 방어만 둬서 정산 금액이 조용히 틀려지는 것만 막는다.
 */
@Service
@RequiredArgsConstructor
public class SettlementService {

    // TODO CommissionRate 엔티티/API 생기면 전역 기본값 + 행사별 override 조회로 교체한다.
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.0500");

    private static final String PENDING = "PENDING";
    private static final String COMPLETED = "COMPLETED";

    private final SettlementMapper settlementMapper;
    private final PaymentMapper paymentMapper;
    private final RefundMapper refundMapper;

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
        long commissionAmount = DEFAULT_COMMISSION_RATE
                .multiply(BigDecimal.valueOf(netBeforeCommission))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long netAmount = netBeforeCommission - commissionAmount;

        LocalDateTime now = LocalDateTime.now();
        SettlementRow row = new SettlementRow();
        row.setFairId(fairId);
        row.setBusinessId(businessId);
        row.setGrossAmount(grossAmount);
        row.setRefundAmount(refundAmount);
        row.setCommissionRate(DEFAULT_COMMISSION_RATE);
        row.setCommissionAmount(commissionAmount);
        row.setNetAmount(netAmount);
        row.setStatus(PENDING);
        row.setCreatedAt(now);
        row.setUpdatedAt(now);

        try {
            settlementMapper.insert(row);

            if (!items.isEmpty()) {
                for (SettlementItemRow item : items) {
                    item.setSettlementId(row.getSettlementId());
                }
                // UK_SETTLEMENT_ITEM_PAYMENT(payment_id 전역 유니크) 위반 시에도 여기서 잡는다 —
                // 같은 결제가 다른 정산에 이미 포함돼 있으면(정상 흐름에선 fair+business 선점 체크로
                // 막히지만, 방어적으로) 500 대신 의미 있는 409로 응답한다.
                settlementMapper.insertItems(items);
            }
        } catch (DuplicateKeyException e) {
            throw new CommonException(ErrorCode.SETTLEMENT_ALREADY_EXISTS);
        }

        return SettlementResponse.from(row);
    }

    /**
     * 정산을 확정한다(SUPER_ADMIN). 확정 이후 금액은 불변 — 재계산·정정은 지원하지 않는다
     * (알려진 한계, WBS 정산 규칙: "확정 이후 변경은 감사기록 필수"이지만 그 재계산 절차는 미구현).
     *
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_FOUND} 존재하지 않는 정산일 때
     * @throws CommonException {@link ErrorCode#SETTLEMENT_NOT_CONFIRMABLE} PENDING이 아닐 때
     */
    public SettlementResponse confirm(Long settlementId, Long confirmedByUserId) {
        SettlementRow row = settlementMapper.selectById(settlementId);
        if (row == null) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_FOUND);
        }
        if (!PENDING.equals(row.getStatus())) {
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_CONFIRMABLE);
        }

        LocalDateTime now = LocalDateTime.now();
        int updated = settlementMapper.confirm(settlementId, confirmedByUserId, now, now);
        if (updated == 0) {
            // selectById 이후 이 UPDATE 사이에 동시에 다른 요청이 먼저 확정한 경우(동시성 방어)
            throw new CommonException(ErrorCode.SETTLEMENT_NOT_CONFIRMABLE);
        }

        row.setStatus("CONFIRMED");
        row.setConfirmedByUserId(confirmedByUserId);
        row.setConfirmedAt(now);
        return SettlementResponse.from(row);
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
