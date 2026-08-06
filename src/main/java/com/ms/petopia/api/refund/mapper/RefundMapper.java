package com.ms.petopia.api.refund.mapper;

import com.ms.petopia.api.refund.dto.RefundRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * REFUND 테이블 조회용 매퍼.
 *
 * <p>XML은 {@code mapper/refund/RefundMapper.xml}에 있다({@code classpath:mapper/**}/*.xml 규칙).
 */
@Mapper
public interface RefundMapper {

    /** 환불 PK로 한 건 조회한다. 없으면 null. */
    RefundRow selectById(@Param("refundId") Long refundId);

    /**
     * 결제 PK로 환불 한 건을 조회한다(UK_REFUND_PAYMENT 덕분에 결제 1건당 환불은 최대 1건).
     * 정산 집계 시 "이 결제가 환불됐는지"를 판단하는 데 쓴다.
     */
    RefundRow selectByPaymentId(@Param("paymentId") Long paymentId);

    /**
     * 환불 한 건을 생성한다. row.paymentId가 이미 환불된 적 있으면(UK_REFUND_PAYMENT 위반)
     * DuplicateKeyException이 던져진다 — 서비스 계층에서 REFUND_ALREADY_PROCESSED로 변환한다.
     *
     * <p>insert 후 row.refundId에 생성된 PK가 채워진다(XML의 useGeneratedKeys).
     */
    void insert(RefundRow row);
}
