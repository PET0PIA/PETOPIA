package com.ms.petopia.api.reservation.dto;

public record GateScanRequest(
        String qrToken,
        String gateName,
        String deviceInfo
) {
}
