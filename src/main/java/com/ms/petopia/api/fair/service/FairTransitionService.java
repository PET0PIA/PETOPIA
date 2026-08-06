package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.FairTransitionRow;
import com.ms.petopia.api.fair.mapper.FairTransitionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * fairs.status 자동전이 3종을 처리한다. reservation 도메인의
 * {@code ReservationExpirationService}와 같은 구조: 배치 조회(FOR UPDATE SKIP LOCKED) 후
 * 행마다 조건부 UPDATE, 갱신된 건수만 집계한다.
 *
 * <p>PAYMENT_PENDING -> PREPARING(개설비 결제 완료)은 시간이 아니라 결제 이벤트로 일어나는
 * 전이라 이 스케줄러의 대상이 아니다(결제 연동 작업에서 처리). 그래서 PREPARING은 현재
 * 어디서도 채워지지 않고, startDueFairs()는 결제 연동이 붙기 전까지는 실질적으로 항상 0건이다.
 *
 * <p>변경 이력(감사 로그)은 이번 범위에 포함하지 않았다. audit 도메인의 {@code ActionType}에
 * 자동전이용 값을 추가해야 하는데, 이 enum은 다른 도메인 소유라 별도 확인 후 진행한다.
 */
@Service
@RequiredArgsConstructor
public class FairTransitionService {

    private final FairTransitionMapper transitionMapper;
    private final FairTimeProvider timeProvider;

    /**
     * 개설비 결제 기한이 지난 PAYMENT_PENDING 행사를 EXPIRED로 바꾼다.
     */
    @Transactional
    public int expireDuePayments(int batchSize) {
        validateBatchSize(batchSize);
        LocalDateTime now = timeProvider.now();
        List<FairTransitionRow> rows = transitionMapper.selectPaymentExpiringForUpdate(now, batchSize);

        int expired = 0;
        for (FairTransitionRow row : rows) {
            if (transitionMapper.expireFair(row.getFairId(), now) == 1) {
                expired++;
            }
        }
        return expired;
    }

    /**
     * 운영 시작일이 된 PREPARING 행사를 IN_PROGRESS로 바꾼다.
     */
    @Transactional
    public int startDueFairs(int batchSize) {
        validateBatchSize(batchSize);
        LocalDateTime now = timeProvider.now();
        LocalDate today = now.toLocalDate();
        List<FairTransitionRow> rows = transitionMapper.selectPreparingToStartForUpdate(today, batchSize);

        int started = 0;
        for (FairTransitionRow row : rows) {
            if (transitionMapper.startFair(row.getFairId(), now) == 1) {
                started++;
            }
        }
        return started;
    }

    /**
     * 운영 종료일이 지난(다음날이 된) IN_PROGRESS 행사를 ENDED로 바꾼다.
     */
    @Transactional
    public int endDueFairs(int batchSize) {
        validateBatchSize(batchSize);
        LocalDateTime now = timeProvider.now();
        LocalDate today = now.toLocalDate();
        List<FairTransitionRow> rows = transitionMapper.selectInProgressToEndForUpdate(today, batchSize);

        int ended = 0;
        for (FairTransitionRow row : rows) {
            if (transitionMapper.endFair(row.getFairId(), now) == 1) {
                ended++;
            }
        }
        return ended;
    }

    private void validateBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }
}
