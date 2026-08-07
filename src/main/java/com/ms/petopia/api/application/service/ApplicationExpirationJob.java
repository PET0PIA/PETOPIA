package com.ms.petopia.api.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationExpirationJob {

    // 한 번 실행될 때 최대 몇 건까지 처리할지 - 너무 많으면 트랜잭션이 오래 걸려 다른 요청과 락 경합이 커진다
    private static final int BATCH_SIZE = 200;

    private final ApplicationExpirationService applicationExpirationService;

    // 기본 60초 간격으로 실행 (application-local.yaml 등에서 petopia.application.payment-expiration-check-interval-ms로 조정 가능)
    @Scheduled(fixedDelayString = "${petopia.application.payment-expiration-check-interval-ms:60000}")
    public void expirePaymentPendingApplications() {

        int expired = applicationExpirationService.expireDueApplications(BATCH_SIZE);

        if(expired > 0) {
            log.info("결제 기한이 지난 참가 신청을 자동 취소했습니다. count={}", expired);
        }

    }

}
