package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.AdminReservationRow;
import com.ms.petopia.api.reservation.dto.ReservationCreationContext;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityDateRow;
import com.ms.petopia.api.reservation.dto.ReservationMyActiveDateRow;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityFair;
import com.ms.petopia.api.reservation.dto.ReservationInsertRow;
import com.ms.petopia.api.reservation.dto.ReservationListRow;
import com.ms.petopia.api.reservation.dto.ReservationUserSnapshot;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ReservationMapper {

    ReservationAvailabilityFair selectAvailabilityFair(@Param("fairId") Long fairId);

    List<ReservationAvailabilityDateRow> selectAvailabilityDates(
            @Param("fairId") Long fairId,
            @Param("today") LocalDate today,
            @Param("now") LocalDateTime now
    );

    /**
     * 한 행사에서 내가 이미 잡아둔(중복 판정에 걸리는) 예약을 방문일별로 가져온다.
     * 예약 화면 날짜 카드에 "이미 예약함"을 미리 표시하는 용도다.
     */
    List<ReservationMyActiveDateRow> selectMyActiveReservationDates(
            @Param("fairId") Long fairId,
            @Param("userId") Long userId
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
            @Param("userId") Long userId,
            @Param("visitDate") LocalDate visitDate
    );

    ReservationUserSnapshot selectUserSnapshot(@Param("userId") Long userId);

    boolean isAssignedEventAdmin(
            @Param("fairId") Long fairId,
            @Param("adminUserId") Long adminUserId
    );

    List<AdminReservationRow> selectByFairForAdmin(
            @Param("fairId") Long fairId,
            @Param("visitDate") LocalDate visitDate,
            @Param("status") String status,
            @Param("offset") long offset,
            @Param("limit") int limit
    );

    long countByFairForAdmin(
            @Param("fairId") Long fairId,
            @Param("visitDate") LocalDate visitDate,
            @Param("status") String status
    );

    int insertReservation(ReservationInsertRow row);

    int insertCreatedHistory(
            @Param("reservationId") Long reservationId,
            @Param("changedBy") Long changedBy,
            @Param("status") String status
    );
}
