package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.EntryQrIssueContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface EntryMapper {

    EntryQrIssueContext selectQrIssueContext(@Param("reservationId") Long reservationId);

    boolean existsEntryQr(@Param("reservationId") Long reservationId);

    int insertEntryQr(
            @Param("reservationId") Long reservationId,
            @Param("tokenHash") String tokenHash,
            @Param("availableFrom") LocalDateTime availableFrom,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now
    );
}
