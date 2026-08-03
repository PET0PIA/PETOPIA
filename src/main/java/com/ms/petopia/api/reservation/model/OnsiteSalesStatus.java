package com.ms.petopia.api.reservation.model;

import java.util.Locale;
import java.util.Optional;

public enum OnsiteSalesStatus {
    CLOSED,
    OPEN,
    PAUSED;

    public static Optional<OnsiteSalesStatus> from(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
