package com.ms.petopia.api.fair.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * fairs.status 자동전이 3종(EXPIRED/IN_PROGRESS/ENDED)을 주기적으로 실행한다.
 * reservation 도메인의 {@code ReservationExpirationJob}과 동일하게 단일 인스턴스 실행을
 * 전제로 한다(다중 인스턴스 배포 시 분산 락 필요 - 현재 스코프 밖).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FairTransitionJob {

    private static final int BATCH_SIZE = 200;

    private final FairTransitionService transitionService;

    @Scheduled(fixedDelayString = "${petopia.fair.transition-check-interval-ms:300000}")
    public void runTransitions() {
        int expired = transitionService.expireDuePayments(BATCH_SIZE);
        if (expired > 0) {
            log.info("개설비 결제 기한이 지난 행사를 만료 처리했습니다. count={}", expired);
        }

        int started = transitionService.startDueFairs(BATCH_SIZE);
        if (started > 0) {
            log.info("운영 시작일이 된 행사를 진행중으로 전환했습니다. count={}", started);
        }

        int ended = transitionService.endDueFairs(BATCH_SIZE);
        if (ended > 0) {
            log.info("운영 종료일이 지난 행사를 종료 처리했습니다. count={}", ended);
        }
    }
}
