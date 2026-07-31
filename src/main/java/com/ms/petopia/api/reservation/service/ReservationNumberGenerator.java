package com.ms.petopia.api.reservation.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 사용자 노출용 예약번호를 생성한다.
 */
@Component
public class ReservationNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    public String generate(LocalDate date) {
        String randomPart = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        return "R" + date.format(DATE_FORMAT) + randomPart;
    }
}
