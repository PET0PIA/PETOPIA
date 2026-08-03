package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.ReservationChangeFairDateRow;
import com.ms.petopia.api.reservation.dto.ReservationChangeReservationRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationChangeMapper {

    ReservationChangeReservationRow selectReservationForUpdate(@Param("reservationId") Long reservationId);

    List<ReservationChangeFairDateRow> selectFairDatesForUpdate(
            @Param("fairId") Long fairId,
            @Param("operationDates") List<LocalDate> operationDates
    );

    int countCapacityOccupyingAdvanceReservations(
            @Param("fairId") Long fairId,
            @Param("visitDate") LocalDate visitDate
    );

    int updateVisitDate(
            @Param("reservationId") Long reservationId,
            @Param("visitDate") LocalDate visitDate,
            @Param("now") LocalDateTime now
    );

    int insertVisitDateChangedHistory(
            @Param("reservationId") Long reservationId,
            @Param("previousVisitDate") LocalDate previousVisitDate,
            @Param("visitDate") LocalDate visitDate,
            @Param("changedBy") Long changedBy,
            @Param("now") LocalDateTime now
    );
}
