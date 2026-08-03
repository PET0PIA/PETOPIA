package com.ms.petopia.api.reservation.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class EntryQrScanContext {
    private Long entryQrId;
    private Long reservationId;
    private Long fairId;
    private Long userId;
    private String reservationType;
    private String reservationStatus;
    private LocalDateTime availableFrom;
    private LocalDateTime expiresAt;
    private Long entryRecordId;
    private LocalDateTime firstCheckedInAt;
}
