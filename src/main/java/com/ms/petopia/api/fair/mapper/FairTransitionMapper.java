package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.fair.dto.FairTransitionRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 상태 자동전이 스케줄러 전용 쿼리. {@code FairMapper}는 entity 단위 기본 CRUD만 담당하므로
 * (해당 파일 javadoc 참고) 배치 작업 전용 쿼리는 이 매퍼로 분리한다.
 * reservation 도메인의 {@code ReservationExpirationMapper}와 동일한 패턴 -
 * 조회는 {@code FOR UPDATE SKIP LOCKED}로 행을 잠그고, 갱신은 상태 조건까지 다시 확인하는
 * 조건부 UPDATE로 한다(조회와 갱신 사이 동시성 문제를 갱신 쿼리의 WHERE 조건으로 방어).
 */
@Mapper
public interface FairTransitionMapper {

    /**
     * 개설비 결제 기한(payment_due_at)이 지난 PAYMENT_PENDING 행사를 조회한다.
     */
    List<FairTransitionRow> selectPaymentExpiringForUpdate(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    /**
     * PAYMENT_PENDING -> EXPIRED. 조회 이후 이미 다른 트랜잭션이 상태를 바꿨으면 0을 반환한다.
     */
    int expireFair(@Param("fairId") Long fairId, @Param("now") LocalDateTime now);

    /**
     * 운영 시작일(operation_start_date)이 도래한 PREPARING 행사를 조회한다.
     */
    List<FairTransitionRow> selectPreparingToStartForUpdate(
            @Param("today") LocalDate today,
            @Param("limit") int limit
    );

    /**
     * PREPARING -> IN_PROGRESS.
     */
    int startFair(@Param("fairId") Long fairId, @Param("now") LocalDateTime now);

    /**
     * 운영 종료일(operation_end_date)이 지난 IN_PROGRESS 행사를 조회한다. 당일까지는 진행중으로
     * 보고, 다음날부터 종료 대상이 된다({@code operation_end_date < today}).
     */
    List<FairTransitionRow> selectInProgressToEndForUpdate(
            @Param("today") LocalDate today,
            @Param("limit") int limit
    );

    /**
     * IN_PROGRESS -> ENDED.
     */
    int endFair(@Param("fairId") Long fairId, @Param("now") LocalDateTime now);

    /**
     * 개설비(FAIR_OPENING_FEE) 결제가 COMPLETED로 끝난 PAYMENT_PENDING 행사를 조회한다.
     * payment 테이블은 결제 도메인 소유라 EXISTS 서브쿼리로 읽기만 한다 - FOR UPDATE는
     * fairs 쪽 행에만 걸린다(서브쿼리 대상인 payment 행은 잠그지 않는다).
     *
     * <p>결제 완료를 이 도메인이 어떻게 감지할지는 결제 도메인 API 명세(PaymentService.
     * payFairOpeningFee 참고)에 "미확정"으로 남아있었는데, confirmPayment()가 예약금과
     * 달리 개설비는 크로스도메인 콜백을 보내지 않는 걸 확인해서(코드 확인, 2026-08-07)
     * 폴링으로 결정했다 - expireDuePayments/startDueFairs/endDueFairs와 같은 방식이고,
     * 콜백 발행 쪽(결제 도메인)이 아직 없어도 이 도메인 혼자 만들 수 있다는 실용적 이유도 있다.
     */
    List<FairTransitionRow> selectPaymentCompletedForUpdate(@Param("limit") int limit);

    /**
     * PAYMENT_PENDING -> PREPARING(개설비 결제 완료). 조회(selectPaymentCompletedForUpdate)의
     * COMPLETED 확인은 별도 쿼리라, 그 사이 개설비 결제가 수동 환불 등으로 상태가 바뀌면
     * 낡은 판단으로 전이해버릴 수 있다 - 그래서 이 UPDATE도 같은 EXISTS 조건을 WHERE에 걸어
     * 갱신 시점에 결제 완료 여부를 다시 확인한다(XML 참고).
     */
    int completeFairPayment(@Param("fairId") Long fairId, @Param("now") LocalDateTime now);
}
