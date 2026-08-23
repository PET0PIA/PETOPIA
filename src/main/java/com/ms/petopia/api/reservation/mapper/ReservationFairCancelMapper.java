package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.CanceledFairReservationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 취소된 행사에 남아 있는 예약을 찾아 CANCELED로 정리한다.
 *
 * <p>{@code fairs}·{@code payment}·{@code refund}는 다른 도메인 소유 테이블이라 <b>읽기만</b> 한다
 * (FairTransitionMapper가 payment를 읽는 방식과 같은 규칙). 잠금은 이 도메인 테이블인
 * {@code reservations}에만 건다.
 */
@Mapper
public interface ReservationFairCancelMapper {

    /**
     * 취소된 행사(fairs.canceled_at IS NOT NULL)에 묶인 채 아직 살아 있는 예약을 잠그고 가져온다.
     *
     * <p>CHECKED_IN은 대상이 아니다 - 이미 입장한 이력을 뒤집으면 안 된다.
     * EXPIRED·CANCELED는 이미 끝난 예약이라 손댈 게 없다.
     */
    List<CanceledFairReservationRow> selectLiveReservationsOfCanceledFairsForUpdate(
            @Param("limit") int limit
    );

    /**
     * 예약을 CANCELED로 전환한다. 조회와 갱신 사이에 상황이 바뀌었을 수 있으므로
     * (사용자가 직접 취소, 결제 완료 통지로 상태 상승 등) 상태 CAS와 행사 취소 여부를 다시 확인한다.
     *
     * @param requireRefundCompleted true면 "예약금 결제가 COMPLETED 환불까지 끝났을 때"만 전환한다.
     *                               조회 시점 판정을 갱신 시점에 한 번 더 확인하는 장치다.
     * @return 1이면 이번 호출이 취소를 성사시켰다(정원 반납·이력은 이때만 해야 한다), 0이면 아무것도 안 했다
     */
    int cancelByFairCancellation(
            @Param("reservationId") Long reservationId,
            @Param("expectedStatus") String expectedStatus,
            @Param("reason") String reason,
            @Param("requireRefundCompleted") boolean requireRefundCompleted,
            @Param("now") LocalDateTime now
    );

    /**
     * 행사 취소로 인한 자동 취소 이력을 남긴다. 사용자가 직접 취소한 이력
     * ({@code RESERVATION_CANCELED}, actor_type=USER)과 구분하려고 action_type을 따로 쓴다.
     */
    int insertFairCanceledHistory(
            @Param("reservationId") Long reservationId,
            @Param("previousStatus") String previousStatus,
            @Param("reason") String reason,
            @Param("refundId") Long refundId,
            @Param("refundAmount") Long refundAmount,
            @Param("now") LocalDateTime now
    );
}
