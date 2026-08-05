package com.ms.petopia.api.payment.mapper;

import com.ms.petopia.api.payment.dto.PaymentRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * PAYMENT 테이블 조회용 매퍼.
 *
 * <p>{@code @MapperScan}을 두지 않았으므로 {@code @Mapper}가 붙은 인터페이스만 빈으로 등록된다.
 * XML은 {@code classpath:mapper/**}/*.xml 규칙에 따라 {@code mapper/payment/PaymentMapper.xml}에 있다.
 */
@Mapper
public interface PaymentMapper {

    /**
     * 결제 PK로 한 건 조회한다. 없으면 null을 반환한다(예외로 던지지 않음 — 존재 유무 판단은
     * 서비스 계층의 책임으로 남겨둔다).
     */
    PaymentRow selectById(@Param("paymentId") Long paymentId);

    /**
     * 예약 PK로 그 예약의 결제를 조회한다(RESERVATION_DEPOSIT 전용). 예약 도메인이 환불 API를
     * 부르기 전에 paymentId를 알아내는 용도. 여러 건이 있을 수 없다 — idempotencyKey가
     * reservationId 기준이라 예약 하나당 예약금 결제는 최대 1건.
     */
    PaymentRow selectByReservationId(@Param("reservationId") Long reservationId);

    /**
     * 결제 한 건을 생성한다. row.idempotencyKey가 이미 존재하면(동일 대상 중복결제)
     * DB의 UK_PAYMENT_IDEMPOTENCY_KEY 위반으로 DuplicateKeyException이 던져진다 —
     * 서비스 계층에서 잡아서 비즈니스 예외로 변환한다.
     *
     * <p>insert 후 row.paymentId에 생성된 PK가 채워진다(XML의 useGeneratedKeys).
     */
    void insert(PaymentRow row);

    /**
     * 토스 승인 API를 부르기 전에 PENDING -> PROCESSING으로 원자적으로 선점한다.
     * 동시에 두 요청이 들어와도 이 UPDATE 자체가 원자적이라 딱 하나만 1을 받고,
     * 나머지는 0을 받는다 — 0을 받은 쪽은 토스를 아예 호출하지 않아야 한다
     * (두 요청이 동시에 토스 승인 API를 부르는 것 자체를 막는 게 목적).
     */
    int markProcessing(@Param("paymentId") Long paymentId, @Param("updatedAt") LocalDateTime updatedAt);

    /** PROCESSING -> COMPLETED. markProcessing으로 선점에 성공한 요청만 호출한다. */
    int markCompleted(PaymentRow row);

    /**
     * 토스가 확정적으로 승인을 거부했을 때(4xx) PROCESSING -> FAILED로 전이한다.
     * markProcessing으로 선점에 성공한 요청만 호출하므로, 정상 흐름에서는 항상 1을 반환한다.
     */
    int markFailed(@Param("paymentId") Long paymentId, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 정산 집계용 — 특정 행사·업체의 완료된 참가비(VENDOR_FEE) 결제 전체를 조회한다.
     * 정산대상은 "결제완료된 참가비"만이라 status/paymentType을 XML에서 고정한다.
     */
    List<PaymentRow> selectCompletedVendorFeePayments(
            @Param("fairId") Long fairId, @Param("businessId") Long businessId);
}
