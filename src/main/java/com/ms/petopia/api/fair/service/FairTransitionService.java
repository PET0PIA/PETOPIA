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
 * fairs.status 자동전이 4종을 처리한다. reservation 도메인의
 * {@code ReservationExpirationService}와 같은 구조: 배치 조회(FOR UPDATE SKIP LOCKED) 후
 * 행마다 조건부 UPDATE, 갱신된 건수만 집계한다.
 *
 * <p>PAYMENT_PENDING -> PREPARING(개설비 결제 완료)도 이제 이 스케줄러가 처리한다(폴링).
 * 처음엔 "시간이 아니라 결제 이벤트로 일어나는 전이라 스케줄러 대상이 아니다"로 판단했었는데,
 * 결제 도메인의 {@code PaymentService.payFairOpeningFee}/{@code confirmPayment} 코드를
 * 확인해보니 참가비와 마찬가지로 개설비 결제 완료 시 크로스도메인 콜백을 보내지 않고
 * (예약금만 {@code notifyReservationDomain}으로 통지함), API 명세에도 "행사 도메인이
 * 결제 완료를 어떻게 감지할지(폴링/이벤트 발행) 미확정"으로 남아있었다. 콜백 발행 쪽(결제
 * 도메인)이 아직 없어도 이 도메인 혼자 만들 수 있다는 실용적 이유로 폴링을 선택했다 -
 * 나중에 결제 도메인이 콜백을 만들면 그쪽으로 옮기고 이 폴링은 안전망으로만 남겨도 된다.
 *
 * <p>변경 이력(감사 로그)은 이번 범위에 포함하지 않았다. audit 도메인의 {@code ActionType}에
 * 자동전이용 값을 추가해야 하는데, 이 enum은 다른 도메인 소유라 별도 확인 후 진행한다.
 *
 * <p>네 조회 쿼리 모두 {@code canceled_at IS NOT NULL}인 행사는 제외한다 - 취소된 행사를
 * 계속 자동전이시켜 봐야 의미가 없고, status만 계속 바뀌면 관리자 화면에서 취소된 행사가
 * 마치 계속 진행 중인 것처럼 보일 수 있다. 취소 여부 자체는 status가 아니라
 * {@code fairs.canceled_at}로 별도 관리한다({@link com.ms.petopia.api.fair.dto.FairStatus} 참고).
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

    /**
     * 개설비 결제가 완료된 PAYMENT_PENDING 행사를 PREPARING으로 바꾼다.
     */
    @Transactional
    public int completeDuePayments(int batchSize) {
        validateBatchSize(batchSize);
        LocalDateTime now = timeProvider.now();
        List<FairTransitionRow> rows = transitionMapper.selectPaymentCompletedForUpdate(batchSize);

        int completed = 0;
        for (FairTransitionRow row : rows) {
            if (transitionMapper.completeFairPayment(row.getFairId(), now) == 1) {
                completed++;
            }
        }
        return completed;
    }

    private void validateBatchSize(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
    }
}
