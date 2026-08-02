package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.ExpiringReservationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationExpirationMapper {

    List<ExpiringReservationRow> selectDueReservationsForUpdate(
            @Param("now") LocalDateTime now,
            @Param("limit") int limit
    );

    int expirePendingReservation(
            @Param("reservationId") Long reservationId,
            @Param("now") LocalDateTime now
    );

    int insertExpiredHistory(
            @Param("reservationId") Long reservationId,
            @Param("now") LocalDateTime now
    );
}
