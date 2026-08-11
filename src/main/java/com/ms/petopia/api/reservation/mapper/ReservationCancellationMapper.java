package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.ReservationCancellationContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface ReservationCancellationMapper {

    ReservationCancellationContext selectCancellationContextForUpdate(
            @Param("reservationId") Long reservationId
    );

    int cancelReservation(
            @Param("reservationId") Long reservationId,
            @Param("expectedStatus") String expectedStatus,
            @Param("reason") String reason,
            @Param("canceledBy") Long canceledBy,
            @Param("now") LocalDateTime now
    );

    /**
     * 취소 이력을 남긴다. 예약금을 환불한 취소면 refundId/refundAmount가 함께 기록돼,
     * 이력만 보고도 "환불까지 나간 취소인지"를 알 수 있다(환불이 없으면 둘 다 null).
     */
    int insertCanceledHistory(
            @Param("reservationId") Long reservationId,
            @Param("previousStatus") String previousStatus,
            @Param("reason") String reason,
            @Param("changedBy") Long changedBy,
            @Param("refundId") Long refundId,
            @Param("refundAmount") Long refundAmount,
            @Param("now") LocalDateTime now
    );
}
