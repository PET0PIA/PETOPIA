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

    int insertCanceledHistory(
            @Param("reservationId") Long reservationId,
            @Param("previousStatus") String previousStatus,
            @Param("reason") String reason,
            @Param("changedBy") Long changedBy,
            @Param("now") LocalDateTime now
    );
}
