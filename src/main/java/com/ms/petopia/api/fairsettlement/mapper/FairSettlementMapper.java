package com.ms.petopia.api.fairsettlement.mapper;

import com.ms.petopia.api.fairsettlement.dto.FairSettlementItemRow;
import com.ms.petopia.api.fairsettlement.dto.FairSettlementRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * FAIR_SETTLEMENT / FAIR_SETTLEMENT_ITEM 테이블 조회용 매퍼(2026-08-22, 행사별 최종정산).
 * 기존 SettlementMapper(업체별 정산)와 같은 패턴을 따르되 businessId 없이 fairId 하나로만
 * 다룬다. XML은 {@code mapper/fairsettlement/FairSettlementMapper.xml}에 있다.
 */
@Mapper
public interface FairSettlementMapper {

    FairSettlementRow selectById(@Param("fairSettlementId") Long fairSettlementId);

    /** UK_FAIR_SETTLEMENT_FAIR 덕분에 행사당 정산은 최대 1건. */
    FairSettlementRow selectByFairId(@Param("fairId") Long fairId);

    /**
     * 결제 PK가 포함된 행사 정산의 PK를 조회한다(UK_FAIR_SETTLEMENT_ITEM_PAYMENT 덕분에
     * 결제 1건당 최대 1행). 포함된 정산이 없으면 null. RefundService가
     * {@link #markNeedsRecalculation} 호출 대상을 찾는 데 쓴다.
     */
    Long selectFairSettlementIdByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * PENDING 정산에 "재계산 필요" 표시를 원자적으로 남긴다(settlement 도메인의
     * markNeedsRecalculation과 동일 패턴 — confirm의 원자적 확정 UPDATE와 경쟁해도
     * 먼저 커밋하는 쪽이 이긴다).
     */
    int markNeedsRecalculation(@Param("fairSettlementId") Long fairSettlementId);

    /**
     * 정산 한 건을 생성한다(PENDING). 같은 행사로 이미 계산된 정산이 있으면
     * UK_FAIR_SETTLEMENT_FAIR 위반으로 DuplicateKeyException.
     */
    void insert(FairSettlementRow row);

    /** 정산 감사근거 상세 내역 일괄 저장. items가 비어있으면 호출하지 않는다(서비스 책임). */
    void insertItems(@Param("items") List<FairSettlementItemRow> items);

    /** 재계산 전 기존 감사근거 내역을 전부 지운다. */
    int deleteItemsByFairSettlementId(@Param("fairSettlementId") Long fairSettlementId);

    /**
     * PENDING -> CONFIRMED로 원자적으로 확정한다. 이미 확정됐거나, 없는 정산이거나, 재계산이
     * 필요한 상태(needs_recalculation)면 0을 반환한다.
     */
    int confirm(@Param("fairSettlementId") Long fairSettlementId,
                @Param("confirmedByUserId") Long confirmedByUserId,
                @Param("confirmedAt") LocalDateTime confirmedAt,
                @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 재계산된 금액을 PENDING 상태인 정산에만 원자적으로 반영한다(동시성 방어,
     * status='PENDING' 조건으로 성공 여부 판단).
     */
    int updateAggregates(@Param("fairSettlementId") Long fairSettlementId,
                          @Param("grossAmount") Long grossAmount,
                          @Param("refundAmount") Long refundAmount,
                          @Param("commissionAmount") Long commissionAmount,
                          @Param("netAmount") Long netAmount,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * CONFIRMED -> PENDING으로 원자적으로 되돌린다(정정 절차). confirmed_at/confirmed_by_user_id도
     * 같이 비운다. 이미 PENDING이거나 없는 정산이면 0을 반환한다.
     */
    int reopen(@Param("fairSettlementId") Long fairSettlementId,
               @Param("updatedAt") LocalDateTime updatedAt);
}
