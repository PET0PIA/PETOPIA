package com.ms.petopia.api.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpirationJob {

    private static final int BATCH_SIZE = 200;

    private final ReservationExpirationService expirationService;

    @Scheduled(fixedDelayString = "${petopia.reservation.expiration-check-interval-ms:60000}")
    public void expirePaymentPendingReservations() {
        int expired = expirationService.expireDueReservations(BATCH_SIZE);
        if (expired > 0) {
            log.info("결제 제한시간이 지난 예약을 만료했습니다. count={}", expired);
        }
    }
}
