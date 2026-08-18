package com.ms.petopia.api.reservation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableAsync // 실시간 통계 확인용 - @Async 어노테이션이 동작하도록 활성화
public class ReservationSchedulingConfig {
}
