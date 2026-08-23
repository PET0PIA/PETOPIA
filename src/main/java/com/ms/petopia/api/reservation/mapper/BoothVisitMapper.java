package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.BoothScanContext;
import com.ms.petopia.api.reservation.dto.BoothVisitRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface BoothVisitMapper {

    /**
     * 주어진 부스가 이 VENDOR(활성) 소유일 때만 부스·사업자·행사 식별자를 반환한다.
     * 소유가 아니거나 계정이 유효하지 않으면 null.
     */
    BoothScanContext selectBoothForVendor(
            @Param("boothId") Long boothId,
            @Param("vendorUserId") Long vendorUserId
    );

    /**
     * (예약, 부스) 조합의 기존 방문 기록을 잠금 조회한다. 없으면 null.
     */
    BoothVisitRecord selectBoothVisitForUpdate(
            @Param("reservationId") Long reservationId,
            @Param("boothId") Long boothId
    );

    int insertBoothVisit(
            @Param("fairId") Long fairId,
            @Param("boothId") Long boothId,
            @Param("businessId") Long businessId,
            @Param("userId") Long userId,
            @Param("reservationId") Long reservationId,
            @Param("now") LocalDateTime now
    );

    int updateBoothVisitForRevisit(
            @Param("boothVisitId") Long boothVisitId,
            @Param("now") LocalDateTime now
    );
}
