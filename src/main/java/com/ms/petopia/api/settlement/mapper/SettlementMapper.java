package com.ms.petopia.api.settlement.mapper;

import com.ms.petopia.api.settlement.dto.SettlementItemRow;
import com.ms.petopia.api.settlement.dto.SettlementRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SETTLEMENT / SETTLEMENT_ITEM 테이블 조회용 매퍼.
 *
 * <p>XML은 {@code mapper/settlement/SettlementMapper.xml}에 있다.
 */
@Mapper
public interface SettlementMapper {

    SettlementRow selectById(@Param("settlementId") Long settlementId);

    /** UK_SETTLEMENT_FAIR_BUSINESS 덕분에 행사·사업자 조합당 정산은 최대 1건. */
    SettlementRow selectByFairAndBusiness(@Param("fairId") Long fairId, @Param("businessId") Long businessId);

    /** 행사 하나에 속한 정산 전체(행사 관리자 조회용). */
    List<SettlementRow> selectByFairId(@Param("fairId") Long fairId);

    /**
     * fairId·businessId 둘 다 선택적인 조건 조회(SUPER_ADMIN 전용 통합검색, 2026-08-21).
     * 넘긴 값만 AND로 조합한다 - 서비스 계층이 최소 하나는 채워졌는지 검증한다.
     */
    List<SettlementRow> selectByFilter(@Param("fairId") Long fairId, @Param("businessId") Long businessId);

    /**
     * 결제 PK가 포함된 정산의 PK를 조회한다(UK_SETTLEMENT_ITEM_PAYMENT 덕분에 결제 1건당 최대
     * 1행). 포함된 정산이 없으면 null. {@link #markNeedsRecalculation} 호출 대상을 찾는 데 쓴다.
     *
     * <p>여기엔 별도 잠금이 없지만, 호출자(RefundService.refund)가 이미 같은 결제 행을
     * {@code FOR UPDATE}로 잠근 상태라 calculate()/recalculate()가 이 결제를 새 정산에
     * 포함시키는 것도 그 잠금에 걸려 기다리므로 안전하다.
     */
    Long selectSettlementIdByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * PENDING 정산에 "재계산 필요" 표시를 원자적으로 남긴다. {@code status = 'PENDING'} 조건
     * 덕분에 {@link #confirm}의 원자적 확정 UPDATE와 경쟁해도 같은 SETTLEMENT 행을 두고
     * 먼저 커밋하는 쪽이 이긴다(CodeRabbit 리뷰 지적, PR #54). 환불 서비스가 이 반환값으로
     * "이 결제를 환불해도 되는지"(0이면 이미 PENDING이 아니라는 뜻이라 거부) 판단한다.
     */
    int markNeedsRecalculation(@Param("settlementId") Long settlementId);

    /**
     * 정산 한 건을 생성한다(PENDING). 같은 행사·사업자로 이미 계산된 정산이 있으면
     * UK_SETTLEMENT_FAIR_BUSINESS 위반으로 DuplicateKeyException — 서비스 계층에서
     * SETTLEMENT_ALREADY_EXISTS로 변환한다.
     */
    void insert(SettlementRow row);

    /** 정산 감사근거 상세 내역 일괄 저장. items가 비어있으면 호출하지 않는다(서비스 책임). */
    void insertItems(@Param("items") List<SettlementItemRow> items);

    /** 재계산 전 기존 감사근거 내역을 전부 지운다(SettlementService.recalculate 전용). */
    int deleteItemsBySettlementId(@Param("settlementId") Long settlementId);

    /**
     * PENDING -> CONFIRMED로 원자적으로 확정한다. 이미 확정됐거나, 없는 정산이거나, 재계산이
     * 필요한 상태(needs_recalculation)면 0을 반환한다(동시 확정 요청 방어 — 결제 markProcessing과
     * 같은 패턴, needs_recalculation 조건은 CodeRabbit 리뷰 지적, PR #54).
     */
    int confirm(@Param("settlementId") Long settlementId,
                @Param("confirmedByUserId") Long confirmedByUserId,
                @Param("confirmedAt") LocalDateTime confirmedAt,
                @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 재계산된 금액을 PENDING 상태인 정산에만 원자적으로 반영한다. selectById로 PENDING을
     * 확인한 뒤에도 이 UPDATE 사이에 다른 요청이 먼저 확정해버릴 수 있어(동시성 방어),
     * WHERE 절에 status='PENDING'을 같이 걸고 영향받은 행 수로 성공 여부를 판단한다.
     */
    int updateAggregates(@Param("settlementId") Long settlementId,
                          @Param("grossAmount") Long grossAmount,
                          @Param("refundAmount") Long refundAmount,
                          @Param("commissionAmount") Long commissionAmount,
                          @Param("netAmount") Long netAmount,
                          @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * CONFIRMED -> PENDING으로 원자적으로 되돌린다(정정 절차, SettlementService.reopen 전용).
     * confirmed_at/confirmed_by_user_id도 같이 비워서 되돌아간 뒤엔 "확정 안 된 상태"로
     * 정확히 보이게 한다 — 남겨두면 프론트가 status는 PENDING인데 확정일시는 남아있는
     * 것처럼 헷갈리게 보여줄 수 있다. 이미 PENDING이거나 없는 정산이면 0을 반환한다
     * (동시성 방어 — confirm/updateAggregates와 같은 패턴).
     */
    int reopen(@Param("settlementId") Long settlementId,
               @Param("updatedAt") LocalDateTime updatedAt);
}
