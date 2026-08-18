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

    /**
     * Redis ZSET의 score로 쓸 epoch milli. 대기열이 순번·만료를 숫자 하나로 다루기 위해
     * 필요하다. {@link #now()}와 같은 시계를 봐야 두 값이 어긋나지 않는다.
     */
    public long epochMilli() {
        return now().atZone(SEOUL_ZONE).toInstant().toEpochMilli();
    }
}
