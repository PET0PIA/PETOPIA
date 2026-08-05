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
     * 정산 한 건을 생성한다(PENDING). 같은 행사·업체로 이미 계산된 정산이 있으면
     * UK_SETTLEMENT_FAIR_BUSINESS 위반으로 DuplicateKeyException — 서비스 계층에서
     * SETTLEMENT_ALREADY_EXISTS로 변환한다.
     */
    void insert(SettlementRow row);

    /** 정산 감사근거 상세 내역 일괄 저장. items가 비어있으면 호출하지 않는다(서비스 책임). */
    void insertItems(@Param("items") List<SettlementItemRow> items);

    /**
     * PENDING -> CONFIRMED로 원자적으로 확정한다. 이미 확정됐거나 없는 정산이면 0을 반환한다
     * (동시 확정 요청 방어 — 결제 markProcessing과 같은 패턴).
     */
    int confirm(@Param("settlementId") Long settlementId,
                @Param("confirmedByUserId") Long confirmedByUserId,
                @Param("confirmedAt") LocalDateTime confirmedAt,
                @Param("updatedAt") LocalDateTime updatedAt);
}
