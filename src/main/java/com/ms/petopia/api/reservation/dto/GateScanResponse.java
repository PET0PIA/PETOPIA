package com.ms.petopia.api.reservation.dto;

import java.time.LocalDateTime;

public record GateScanResponse(
        String resultCode,
        boolean firstEntry,
        String entrySource,
        LocalDateTime firstCheckedInAt
) {
}
