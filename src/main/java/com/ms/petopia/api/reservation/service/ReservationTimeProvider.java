package com.ms.petopia.api.reservation.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class ReservationTimeProvider {

    public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    public LocalDate today() {
        return LocalDate.now(SEOUL_ZONE);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(SEOUL_ZONE);
    }
}
