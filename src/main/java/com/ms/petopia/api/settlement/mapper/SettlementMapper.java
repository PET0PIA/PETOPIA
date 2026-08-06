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

    /** UK_SETTLEMENT_FAIR_BUSINESS 덕분에 행사·업체 조합당 정산은 최대 1건. */
    SettlementRow selectByFairAndBusiness(@Param("fairId") Long fairId, @Param("businessId") Long businessId);

    /** 행사 하나에 속한 정산 전체(박람회관리자 조회용). */
    List<SettlementRow> selectByFairId(@Param("fairId") Long fairId);

    /**
     * 결제 PK가 포함된 정산의 상태(PENDING/CONFIRMED)를 조회한다(UK_SETTLEMENT_ITEM_PAYMENT
     * 덕분에 결제 1건당 최대 1행). 환불 서비스가 "CONFIRMED 정산에 포함된 결제는 환불 금지"
     * 방어에 쓴다 — PENDING 정산은 이후 재계산으로 바로잡을 수 있어 환불을 막지 않는다.
     * 포함된 정산이 없으면 null.
     */
    String selectSettlementStatusByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * 정산 한 건을 생성한다(PENDING). 같은 행사·업체로 이미 계산된 정산이 있으면
     * UK_SETTLEMENT_FAIR_BUSINESS 위반으로 DuplicateKeyException — 서비스 계층에서
     * SETTLEMENT_ALREADY_EXISTS로 변환한다.
     */
    void insert(SettlementRow row);

    /** 정산 감사근거 상세 내역 일괄 저장. items가 비어있으면 호출하지 않는다(서비스 책임). */
    void insertItems(@Param("items") List<SettlementItemRow> items);

    /** 재계산 전 기존 감사근거 내역을 전부 지운다(SettlementService.recalculate 전용). */
    int deleteItemsBySettlementId(@Param("settlementId") Long settlementId);

    /**
     * PENDING -> CONFIRMED로 원자적으로 확정한다. 이미 확정됐거나 없는 정산이면 0을 반환한다
     * (동시 확정 요청 방어 — 결제 markProcessing과 같은 패턴).
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
}
