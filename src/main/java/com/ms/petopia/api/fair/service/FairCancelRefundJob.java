package com.ms.petopia.api.fair.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 행사 취소 환불(fair_cancel_refund_targets)을 주기적으로 발견·처리한다.
 * {@code FairTransitionJob}과 같은 단일 인스턴스 실행 전제(다중 인스턴스 배포 시 분산 락
 * 필요 - 현재 스코프 밖).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FairCancelRefundJob {

    private static final int FAIR_BATCH_SIZE = 50;
    private static final int TARGET_BATCH_SIZE = 200;

    private final FairCancelRefundOrchestrationService orchestrationService;

    @Scheduled(fixedDelayString = "${petopia.fair.cancel-refund-check-interval-ms:300000}")
    public void run() {
        int enumerated = orchestrationService.enumerateTargets(FAIR_BATCH_SIZE);
        if (enumerated > 0) {
            log.info("취소된 행사의 환불 대상 결제를 등록했습니다. count={}", enumerated);
        }

        int completed = orchestrationService.processPendingTargets(TARGET_BATCH_SIZE);
        if (completed > 0) {
            log.info("행사 취소 환불을 처리했습니다. count={}", completed);
        }
    }
}
