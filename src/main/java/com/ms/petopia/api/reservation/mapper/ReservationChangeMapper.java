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

    /**
     * 행사가 정한 방문일 변경 가능 기한(입장 몇 시간 전까지). 설정이 없으면 null이다.
     *
     * <p>위 예약 조회에 fairs를 조인하지 않고 따로 읽는다 - 그 쿼리는 {@code FOR UPDATE}라
     * 조인하면 행사 행까지 잠긴다. 동반 허용 여부(selectFairPetAllowed)도 같은 이유로 별도 조회다.
     */
    Integer selectChangeDeadlineHours(@Param("fairId") Long fairId);

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
