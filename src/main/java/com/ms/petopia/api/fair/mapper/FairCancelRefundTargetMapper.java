package com.ms.petopia.api.fair.mapper;

import com.ms.petopia.api.fair.dto.FairCancelRefundTarget;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * fair_cancel_refund_targets 전용 쿼리({@code FairCancelRefundJob} 참고). 두 단계로 나뉜다:
 * (1) 취소된 행사 중 아직 발견 단계를 끝까지 완료하지 않은 행사를 찾아 작업행을 채워 넣는
 * "발견(enumerate)" 단계, (2) PENDING 작업행을 처리하는 "실행" 단계.
 */
@Mapper
public interface FairCancelRefundTargetMapper {

    /**
     * 취소됐지만({@code canceled_at IS NOT NULL}) {@code fair_cancel_refund_enumerations}에
     * 완료 기록이 없는 행사 ID를 조회한다. 작업행({@code fair_cancel_refund_targets}) 존재
     * 여부로 판단하지 않는다 - 참가비 결제가 0건인 행사는 애초에 작업행이 안 생기고,
     * 결제유형 중 하나만 처리하다 실패해도 이미 등록된 행이 있으면 다시 훑을 대상에서
     * 영구히 빠져버리기 때문이다.
     */
    List<Long> selectUnenumeratedCanceledFairIds(@Param("limit") int limit);

    /**
     * 결제 한 건을 환불 작업 대상으로 등록한다. 같은 결제가 이미 등록돼 있으면
     * (UK_FAIR_CANCEL_REFUND_TARGETS_PAYMENT 위반) DuplicateKeyException이 던져진다 -
     * 호출부가 잡아서 무시한다(발견 단계가 여러 번 돌아도 안전하게 하기 위함).
     */
    void insert(FairCancelRefundTarget target);

    /**
     * 행사 하나의 발견 단계가 끝까지(모든 결제유형·모든 페이지) 예외 없이 완료됐음을
     * 기록한다. 이미 기록돼 있으면(동시 실행 등) DuplicateKeyException이 던져진다 -
     * 호출부가 잡아서 무시한다.
     */
    void markEnumerationCompleted(@Param("fairId") Long fairId, @Param("now") LocalDateTime now);

    /**
     * 재시도 대상(PENDING) 작업을 잠가서 조회한다. reservation 도메인의
     * {@code ReservationExpirationMapper}와 동일한 패턴.
     */
    List<FairCancelRefundTarget> selectPendingForUpdate(@Param("limit") int limit);

    /** 환불 성공. */
    int markCompleted(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 재시도해도 성공할 수 없는 실패(이미 환불됨, 정산 확정으로 거부됨 등)를 즉시 FAILED로
     * 확정한다 - 재시도 횟수와 무관하게 바로 끝낸다.
     */
    int markFailed(@Param("id") Long id, @Param("error") String error, @Param("now") LocalDateTime now);

    /**
     * 예상 못한(잠재적으로 일시적인) 실패를 기록한다. 시도 횟수가 maxAttempts에 아직
     * 못 미쳤으면 PENDING을 유지해 다음 스케줄에 다시 시도하고, 도달했으면 FAILED로
     * 확정해 무한 재시도를 막는다.
     */
    int markRetryOrGiveUp(
            @Param("id") Long id,
            @Param("error") String error,
            @Param("maxAttempts") int maxAttempts,
            @Param("now") LocalDateTime now
    );
}
