package com.ms.petopia.api.fair.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * reservation 도메인의 {@code ReservationSchedulingConfig}와 동일한 이유로 존재한다 -
 * {@code @EnableScheduling}을 도메인별 config에 붙여도 스프링이 중복 등록을 알아서
 * 정리하므로 문제없다(이미 reservation 쪽에 선례가 있다).
 */
@Configuration
@EnableScheduling
public class FairSchedulingConfig {
}
