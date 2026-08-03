package com.ms.petopia.api.reservation.mapper;

import com.ms.petopia.api.reservation.dto.EntryQrIssueContext;
import com.ms.petopia.api.reservation.dto.EntryQrScanContext;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface EntryMapper {

    EntryQrIssueContext selectQrIssueContext(@Param("reservationId") Long reservationId);

    boolean existsEntryQr(@Param("reservationId") Long reservationId);

    int updateEntryQrAvailability(
            @Param("reservationId") Long reservationId,
            @Param("availableFrom") LocalDateTime availableFrom,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now
    );

    int insertEntryQr(
            @Param("reservationId") Long reservationId,
            @Param("tokenHash") String tokenHash,
            @Param("availableFrom") LocalDateTime availableFrom,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("now") LocalDateTime now
    );

    EntryQrScanContext selectQrForUpdate(@Param("tokenHash") String tokenHash);

    int insertEntryRecord(
            @Param("fairId") Long fairId,
            @Param("reservationId") Long reservationId,
            @Param("userId") Long userId,
            @Param("entrySource") String entrySource,
            @Param("checkedInAt") LocalDateTime checkedInAt,
            @Param("processedBy") Long processedBy,
            @Param("gateName") String gateName
    );

    int updateEntryRecordForRescan(
            @Param("entryRecordId") Long entryRecordId,
            @Param("scannedAt") LocalDateTime scannedAt
    );

    int markReservationCheckedIn(
            @Param("reservationId") Long reservationId,
            @Param("now") LocalDateTime now
    );

    int insertCheckedInHistory(
            @Param("reservationId") Long reservationId,
            @Param("processedBy") Long processedBy,
            @Param("now") LocalDateTime now
    );

    int insertGateScanLog(
            @Param("entryQrId") Long entryQrId,
            @Param("reservationId") Long reservationId,
            @Param("fairId") Long fairId,
            @Param("resultCode") String resultCode,
            @Param("scannedAt") LocalDateTime scannedAt,
            @Param("processedBy") Long processedBy,
            @Param("gateName") String gateName,
            @Param("deviceInfo") String deviceInfo
    );
}
