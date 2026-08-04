package com.ms.petopia.api.fair.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * reservation 도메인의 {@code ReservationTimeProvider}와 동일한 이유로 존재한다 - 서비스
 * 로직에서 {@code LocalDateTime.now()}를 직접 호출하면 테스트에서 시각을 고정할 수 없어서다.
 */
@Component
public class FairTimeProvider {

    public static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

    public LocalDateTime now() {
        return LocalDateTime.now(SEOUL_ZONE);
    }
}
