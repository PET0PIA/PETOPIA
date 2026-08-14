package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.ReservationCreationContext;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityDateRow;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityFair;
import com.ms.petopia.api.reservation.dto.ReservationInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationListRow;
import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface ReservationMapper {

    ReservationAvailabilityFair selectAvailabilityFair(@Param("fairId") Long fairId);

    List<ReservationAvailabilityDateRow> selectAvailabilityDates(
            @Param("fairId") Long fairId,
            @Param("today") LocalDate today
    );

    List<ReservationListRow> selectMyReservations(
            @Param("userId") Long userId,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    long countMyReservations(@Param("userId") Long userId);

    ReservationListRow selectReservationForOwner(
            @Param("reservationId") Long reservationId,
            @Param("userId") Long userId
    );

    ReservationCreationContext selectCreationContext(
            @Param("fairId") Long fairId,
            @Param("visitDate") LocalDate visitDate
    );

    boolean existsFair(@Param("fairId") Long fairId);

    boolean existsActiveReservation(
            @Param("fairId") Long fairId,
            @Param("userId") Long userId
    );

    ReservationUserSnapshot selectUserSnapshot(@Param("userId") Long userId);

    boolean isAssignedEventAdmin(
            @Param("fairId") Long fairId,
            @Param("adminUserId") Long adminUserId
    );

    int insertReservation(ReservationInsertRow row);

    int insertCreatedHistory(
            @Param("reservationId") Long reservationId,
            @Param("changedBy") Long changedBy,
            @Param("status") String status
    );
}
