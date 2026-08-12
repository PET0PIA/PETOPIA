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
     * selectByPaymentId와 같지만 잠금 읽기다. "이미 환불됐으면 그걸 재사용, 없으면 새로 생성"을
     * 원자적으로 판단해야 하는 {@code RefundService.refundOrReuse}에서만 쓴다.
     *
     * <p>일반 SELECT면 안 되는 이유: 기본 격리수준(REPEATABLE READ)에서 비잠금 읽기는 트랜잭션이
     * 처음 읽은 시점의 스냅샷을 보므로, 그 뒤 다른 트랜잭션(행사 취소 일괄환불 등)이 커밋한 환불
     * 행을 못 본다. 앞서 PAYMENT 행을 FOR UPDATE로 잠갔더라도 마찬가지다 — 최신 커밋을 보장하는
     * 건 잠금 읽기뿐이다. 못 보고 지나가면 INSERT가 UK_REFUND_PAYMENT에 걸려 환불이 실패한다.
     *
     * <p>동시 실행 자체는 이 잠금이 막는 게 아니다 — 환불을 만드는 모든 경로가 먼저
     * {@code PaymentMapper.selectByIdForUpdate}로 같은 PAYMENT 행을 잠그기 때문에 이미 직렬화된다.
     * 이 조회의 역할은 그 직렬화 구간 안에서 "최신 상태를 제대로 읽는 것"이다.
     *
     * <p>reservationId는 채우지 않는다(payment 조인 없음) — 호출자가 이미 잠가서 들고 있는
     * PAYMENT 행에서 채워 넣는다.
     *
     * <p>호출자가 반드시 트랜잭션 안에서 불러야 한다.
     */
    RefundRow selectByPaymentIdForUpdate(@Param("paymentId") Long paymentId);

    /**
     * 환불 한 건을 생성한다. row.paymentId가 이미 환불된 적 있으면(UK_REFUND_PAYMENT 위반)
     * DuplicateKeyException이 던져진다 — 서비스 계층에서 REFUND_ALREADY_PROCESSED로 변환한다.
     *
     * <p>insert 후 row.refundId에 생성된 PK가 채워진다(XML의 useGeneratedKeys).
     */
    void insert(RefundRow row);
}
