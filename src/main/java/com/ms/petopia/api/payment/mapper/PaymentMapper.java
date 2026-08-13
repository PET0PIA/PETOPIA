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
     * selectById와 동일하지만 {@code FOR UPDATE}로 행을 잠근다. 환불(RefundService)과 정산 계산
     * (SettlementService)이 같은 결제 행을 동시에 건드릴 때 서로 직렬화시키는 용도 — 둘 다 이
     * 잠금을 거쳐야 "정산 계산 중에 환불이 끼어들어 SETTLEMENT_ITEM 금액이 옛날 값으로 굳는"
     * 경쟁 조건을 막을 수 있다(CodeRabbit 리뷰 지적, PR #47). 호출자가 반드시 트랜잭션
     * 안에서 불러야 한다.
     */
    PaymentRow selectByIdForUpdate(@Param("paymentId") Long paymentId);

    /**
     * 예약 PK로 그 예약의 결제를 조회한다(RESERVATION_DEPOSIT 전용). 예약 도메인이 환불 API를
     * 부르기 전에 paymentId를 알아내는 용도. 여러 건이 있을 수 없다 — idempotencyKey가
     * reservationId 기준이라 예약 하나당 예약금 결제는 최대 1건.
     */
    PaymentRow selectByReservationId(@Param("reservationId") Long reservationId);

    /**
     * idempotencyKey로 결제를 조회한다. 결제 생성(pay*) 메서드가 insert 전에 "이 원업무에
     * 대한 이전 시도가 있는지, 있다면 FAILED라서 재시도 가능한지" 판단하는 용도로 쓴다.
     * 없으면 null(첫 시도).
     */
    PaymentRow selectByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    /**
     * 참가신청 ID로 그 신청의 참가비 결제를 조회한다(VENDOR_FEE 전용). 채린님(참가업체) 도메인이
     * 참가 취소승인 처리 중 환불 대상 paymentId를 찾는 용도. 취소승인은 결제 전(신청만 하고
     * 아직 결제를 시작 안 한 상태)에도 가능하므로, 결제가 없으면 null을 그대로 반환한다
     * (존재 유무 판단은 서비스 계층 책임).
     */
    PaymentRow selectByApplicationId(@Param("applicationId") Long applicationId);

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
     * PROCESSING -> WAITING_FOR_DEPOSIT. 가상계좌가 발급됐지만 아직 입금 전인 상태 —
     * 토스 confirm 응답이 status="WAITING_FOR_DEPOSIT"를 줄 때만 호출한다(markCompleted 대신).
     * 실제 입금 완료는 이 상태에서 markVirtualAccountCompleted로 한 번 더 전이한다.
     */
    int markWaitingForDeposit(PaymentRow row);

    /**
     * WAITING_FOR_DEPOSIT -> COMPLETED. 가상계좌 입금통지 웹훅(DEPOSIT_CALLBACK, status="DONE")을
     * 받았을 때만 호출한다. {@code WHERE status='WAITING_FOR_DEPOSIT'} 가드가 원자적이라, 토스가
     * 같은 웹훅을 중복 전송해도(재시도) 두 번째부터는 0을 받아 완료 후속처리가 중복 실행되지 않는다.
     */
    int markVirtualAccountCompleted(PaymentRow row);

    /**
     * WAITING_FOR_DEPOSIT -> FAILED. 입금기한 만료 등으로 웹훅이 status="CANCELED"를 줬을 때 호출한다.
     * 가드 방식은 markVirtualAccountCompleted와 동일.
     */
    int markVirtualAccountDepositFailed(@Param("paymentId") Long paymentId, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 토스가 확정적으로 승인을 거부했을 때(4xx) PROCESSING -> FAILED로 전이한다.
     * markProcessing으로 선점에 성공한 요청만 호출하므로, 정상 흐름에서는 항상 1을 반환한다.
     */
    int markFailed(@Param("paymentId") Long paymentId, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * PENDING -> CANCELED로 원자적으로 전이한다(다른 도메인의 취소 처리가 호출, WBS 1.7).
     * markProcessing과 동일하게 {@code WHERE status='PENDING'} 가드라서, 이미 PROCESSING/
     * COMPLETED로 넘어간 결제는 0을 반환한다 — 그 사이 confirm이 먼저 나간 경쟁 상황을 이렇게 막는다.
     */
    int markCanceled(@Param("paymentId") Long paymentId, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * PENDING -> EXPIRED로 원자적으로 전이한다(다른 도메인의 자체 만료 배치가 호출, WBS 1.7).
     * 가드 방식은 markCanceled와 동일.
     */
    int markExpired(@Param("paymentId") Long paymentId, @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 이전 시도가 FAILED로 끝난 결제 행을 PENDING으로 되돌려 재사용한다(결제 3종 공통 —
     * 실패한 원업무가 idempotencyKey UNIQUE 제약에 막혀 영영 재결제 불가능해지는 문제 해결,
     * 2026-08-06 CodeRabbit 지적). markProcessing과 동일하게 {@code WHERE status = 'FAILED'}
     * 가드가 원자적이라, 동시에 두 재시도 요청이 들어와도 하나만 1을 받는다 — 0을 받은 쪽은
     * 이미 다른 요청이 선점했다는 뜻이므로 충돌로 처리해야 한다.
     * 재시도 시점에 금액이 달라질 수 있어(참가비 등 클라이언트가 다시 보내는 값) amount도 같이 갱신한다.
     */
    int resetFailedToPending(
            @Param("paymentId") Long paymentId,
            @Param("amount") Long amount,
            @Param("updatedAt") LocalDateTime updatedAt);

    /**
     * 정산 집계용 — 특정 행사·업체의 완료된 참가비(VENDOR_FEE) 결제 전체를 조회한다.
     * 정산대상은 "결제완료된 참가비"만이라 status/paymentType을 XML에서 고정한다.
     */
    List<PaymentRow> selectCompletedVendorFeePayments(
            @Param("fairId") Long fairId, @Param("businessId") Long businessId);

    /**
     * 조건별 결제 목록 조회. {@code GET /api/payments}(관리자용, fairId·businessId·paymentType·
     * status 조합)와 {@code GET /api/me/payments}(마이페이지, payerUserId만)가 같이 쓴다.
     * 파라미터가 null이면 그 조건은 걸지 않는다(전부 AND로 조합).
     */
    List<PaymentRow> selectByFilter(
            @Param("fairId") Long fairId,
            @Param("businessId") Long businessId,
            @Param("paymentType") String paymentType,
            @Param("status") String status,
            @Param("payerUserId") Long payerUserId,
            @Param("offset") long offset,
            @Param("size") int size);

    /** selectByFilter와 동일 조건으로 전체 건수만 센다(페이지네이션 totalElements용). */
    long countByFilter(
            @Param("fairId") Long fairId,
            @Param("businessId") Long businessId,
            @Param("paymentType") String paymentType,
            @Param("status") String status,
            @Param("payerUserId") Long payerUserId);
}
