package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.ReservationCreationContext;
import com.ms.petopia.api.reservation.dto.ReservationInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface ReservationMapper {

    ReservationCreationContext selectCreationContextForUpdate(
            @Param("fairId") Long fairId,
            @Param("visitDate") LocalDate visitDate
    );

    boolean existsFair(@Param("fairId") Long fairId);

    boolean existsActiveReservation(
            @Param("fairId") Long fairId,
            @Param("userId") Long userId
    );

    int countCapacityOccupyingReservations(
            @Param("fairId") Long fairId,
            @Param("visitDate") LocalDate visitDate
    );

    ReservationUserSnapshot selectUserSnapshot(@Param("userId") Long userId);

    int insertReservation(ReservationInsertRow row);

    int insertCreatedHistory(
            @Param("reservationId") Long reservationId,
            @Param("changedBy") Long changedBy,
            @Param("status") String status
    );
}
