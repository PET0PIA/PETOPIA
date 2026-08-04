package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.FairDateSnapshot;
import com.ms.petopia.api.reservation.dto.OnsiteReservationCreationContext;
import com.ms.petopia.api.reservation.dto.OnsiteSalesPolicyRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Mapper
public interface OnsiteReservationMapper {

    OnsiteReservationCreationContext selectCreationContextForUpdate(
            @Param("fairId") Long fairId,
            @Param("operationDate") LocalDate operationDate
    );

    FairDateSnapshot selectFairDateForUpdate(
            @Param("fairId") Long fairId,
            @Param("fairDateId") Long fairDateId
    );

    FairDateSnapshot selectFairDate(
            @Param("fairId") Long fairId,
            @Param("fairDateId") Long fairDateId
    );

    OnsiteSalesPolicyRow selectPolicy(@Param("fairDateId") Long fairDateId);

    int insertPolicy(
            @Param("fairDateId") Long fairDateId,
            @Param("price") long price,
            @Param("status") String status,
            @Param("updatedBy") Long updatedBy,
            @Param("now") LocalDateTime now
    );

    int updatePolicy(
            @Param("fairDateId") Long fairDateId,
            @Param("price") long price,
            @Param("status") String status,
            @Param("updatedBy") Long updatedBy,
            @Param("expectedVersion") int expectedVersion,
            @Param("now") LocalDateTime now
    );
}
