package com.ms.petopia.api.reservation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 취소된 행사에 남아 있는 예약을 주기적으로 정리한다
 * ({@link ReservationFairCancelSyncService} 참고).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationFairCancelSyncJob {

    private static final int BATCH_SIZE = 200;

    private final ReservationFairCancelSyncService fairCancelSyncService;

    @Scheduled(fixedDelayString = "${petopia.reservation.fair-cancel-sync-interval-ms:60000}")
    public void cancelReservationsOfCanceledFairs() {
        int canceled = fairCancelSyncService.cancelReservationsOfCanceledFairs(BATCH_SIZE);
        if (canceled > 0) {
            log.info("취소된 행사의 예약을 취소 처리했습니다. count={}", canceled);
        }
    }
}
