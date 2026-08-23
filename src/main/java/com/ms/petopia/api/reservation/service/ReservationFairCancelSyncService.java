package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.notification.dto.DeliveryChannel;
import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.RecipientType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.reservation.dto.CanceledFairReservationRow;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import com.ms.petopia.api.reservation.mapper.ReservationFairCancelMapper;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent; // 실시간 통계 확인용
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher; // 실시간 통계 확인용
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 취소된 행사에 남아 있는 예약을 CANCELED로 정리한다.
 *
 * <p><b>왜 예약 도메인이 스스로 훑는가</b>: 행사 취소 승인·환불은 행사 도메인이 처리하지만
 * ({@code FairCancelRequestService} -> {@code FairCancelRefundOrchestrationService}), 그 흐름은
 * 결제를 환불하고 알림을 보내는 데서 끝나고 예약 행은 건드리지 않는다. 그래서 돈은 돌려받았는데
 * 내 예약은 계속 "예약 확정"으로 보이고, 운영일 정원({@code fair_dates.reserved_count})도 잡힌 채
 * 남았다. 행사 도메인에 콜백을 추가하는 대신, 예약 도메인이 {@code fairs.canceled_at}을 읽어
 * 스스로 정리한다 - 도메인 경계를 넘어 남의 코드를 고치지 않고, 행사 쪽 처리가 실패하거나
 * 늦어도 이쪽은 다음 주기에 다시 시도한다.
 *
 * <p><b>언제 취소로 넘기는가</b>: 예약금 결제 상태로만 판정한다. 예약 상태(PENDING_PAYMENT /
 * CONFIRMED)로 나누지 않는 이유는, 판단 기준이 "이 예약에 묶인 돈이 지금 어디 있느냐" 하나이기
 * 때문이다.
 *
 * <table>
 *   <caption>예약금 결제 상태별 처리</caption>
 *   <tr><th>결제 상태</th><th>처리</th></tr>
 *   <tr><td>결제 행 없음(무료 예약), CANCELED, EXPIRED, FAILED</td>
 *       <td>돌려줄 돈이 없다 - 바로 취소한다.</td></tr>
 *   <tr><td>COMPLETED</td>
 *       <td>환불(refund)이 COMPLETED로 남았을 때만 취소한다. 아직이면 이번 회차는 건너뛰고
 *           다음 주기에 다시 본다 - "취소됨"이 곧 "환불 끝"을 뜻하도록 맞춘다.</td></tr>
 *   <tr><td>PENDING, WAITING_FOR_DEPOSIT, PROCESSING</td>
 *       <td>돈이 움직이는 중이라 건너뛴다. 결제창을 띄워둔 사용자가 결제를 마칠 수 있는
 *           상태에서 예약만 취소하면, 취소된 예약에 돈이 들어온다. 이 결제들은 행사 도메인의
 *           {@code FairCancelPendingPaymentService}가 취소하고, 그러지 못한 채 결제 제한시간이
 *           지나면 {@link ReservationExpirationService}가 예약을 EXPIRED로 정리하므로
 *           여기서 무한정 기다리지 않는다.</td></tr>
 * </table>
 *
 * <p>환불이 끝내 실패로 남은 결제(확정된 정산에 묶여 환불이 거부되는 경우 등)는 예약이 계속
 * CONFIRMED로 남는다. 돈을 돌려주지 않은 채 "취소됨"으로 바꾸는 것보다 낫다고 보고 그대로 둔다 -
 * 대신 건수를 로그로 남겨 운영자가 볼 수 있게 한다.
 *
 * <p>한 트랜잭션으로 배치를 처리하는 건 {@link ReservationExpirationService}와 같다. 여기서 하는
 * 일은 전부 같은 DB 안의 갱신이라(환불·결제는 읽기만 한다) 건별로 트랜잭션을 쪼갤 이유가 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationFairCancelSyncService {

    private static final String ADVANCE = "ADVANCE";
    private static final String ONSITE_DIRECT = "ONSITE_DIRECT";
    private static final String PAYMENT_COMPLETED = "COMPLETED";
    private static final String REFUND_COMPLETED = "COMPLETED";

    /** 돈이 움직이는 중이라 예약만 먼저 취소하면 안 되는 결제 상태. */
    private static final Set<String> PAYMENT_IN_FLIGHT_STATUSES =
            Set.of("PENDING", "WAITING_FOR_DEPOSIT", "PROCESSING");

    /** 예약 취소 사유로 남길 문구. 사용자가 상세 화면에서 그대로 본다. */
    static final String CANCEL_REASON = "행사 취소로 인한 자동 취소";

    private final ReservationFairCancelMapper fairCancelMapper;
    private final ReservationCapacityMapper capacityMapper;
    private final ReservationTimeProvider timeProvider;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher; // 실시간 통계 확인용

    /**
     * 취소된 행사의 살아 있는 예약을 정리한다.
     *
     * @return 이번 호출에서 실제로 CANCELED로 바꾼 건수
     */
    @Transactional
    public int cancelReservationsOfCanceledFairs(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }

        LocalDateTime now = timeProvider.now();
        List<CanceledFairReservationRow> rows =
                fairCancelMapper.selectLiveReservationsOfCanceledFairsForUpdate(batchSize);

        int canceled = 0;
        int waiting = 0;
        Set<Long> changedFairIds = new HashSet<>(); // 실시간 통계 확인용
        List<Long> notifyUserIds = new ArrayList<>();

        for (CanceledFairReservationRow row : rows) {
            if (isMoneyInFlight(row)) {
                waiting++;
                continue;
            }
            boolean refundRequired = PAYMENT_COMPLETED.equals(row.getPaymentStatus());
            if (refundRequired && !REFUND_COMPLETED.equals(row.getRefundStatus())) {
                waiting++;
                continue;
            }

            int updated = fairCancelMapper.cancelByFairCancellation(
                    row.getReservationId(),
                    row.getReservationStatus(),
                    CANCEL_REASON,
                    refundRequired,
                    now
            );
            if (updated != 1) {
                // 조회와 갱신 사이에 상태가 바뀌었다(사용자가 직접 취소했거나 결제 완료로 상태가
                // 올라갔거나). 다음 주기에 최신 상태로 다시 판정한다.
                continue;
            }

            fairCancelMapper.insertFairCanceledHistory(
                    row.getReservationId(),
                    row.getReservationStatus(),
                    CANCEL_REASON,
                    row.getRefundId(),
                    row.getRefundAmount(),
                    now
            );

            // 취소된 좌석을 정원에 돌려준다. 상태 전이가 성사된(updated == 1) 건에만 해야
            // 중복 반납이 생기지 않는다. 사전예약과 현장예매는 정원을 따로 센다(V49).
            if (ADVANCE.equals(row.getReservationType())) {
                capacityMapper.release(row.getFairId(), row.getVisitDate());
            } else if (ONSITE_DIRECT.equals(row.getReservationType())) {
                capacityMapper.releaseOnsite(row.getFairId(), row.getVisitDate());
            }

            changedFairIds.add(row.getFairId()); // 실시간 통계 확인용
            if (!refundRequired) {
                // 환불이 나간 건은 행사 도메인이 이미 FAIR_CANCELED와 REFUND_COMPLETED 알림을
                // 보냈다(FairCancelRefundOrchestrationService 참고). 같은 내용을 또 보내지 않는다.
                notifyUserIds.add(row.getUserId());
            }
            canceled++;
        }

        changedFairIds.forEach(id -> eventPublisher.publishEvent(new ReservationStatusChangedEvent(id))); // 실시간 통계 확인용
        registerCancelNotifications(notifyUserIds);

        if (waiting > 0) {
            // 매 주기 남을 수 있는 값이라 INFO로 올리지 않는다. 환불이 끝내 실패해 계속 쌓이는지는
            // 이 로그와 refund 테이블로 확인한다.
            log.debug("행사 취소 예약 정리 대기 중. count={}", waiting);
        }
        return canceled;
    }

    private boolean isMoneyInFlight(CanceledFairReservationRow row) {
        return row.getPaymentStatus() != null && PAYMENT_IN_FLIGHT_STATUSES.contains(row.getPaymentStatus());
    }

    /**
     * 취소 알림은 커밋 뒤에 저장한다 - 배치가 롤백되면 "취소됐다"는 알림만 남는 걸 막는다.
     * 알림 저장 실패가 이미 끝난 취소를 되돌리면 안 되므로 건별로 예외를 삼킨다.
     *
     * <p>IN_APP만 보낸다 - 이 시점 사용자는 이미 FAIR_CANCELED, REFUND_COMPLETED 이메일을
     * 받은 뒤라 "예약이 취소되었습니다" 이메일까지 더하면 중복 안내다. 알림함/마이페이지에서
     * 개별 예약 상태 변경을 확인할 수 있도록 IN_APP 기록만 남긴다.
     */
    private void registerCancelNotifications(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        List<Long> targets = List.copyOf(userIds);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (Long userId : targets) {
                    try {
                        notificationService.save(new SaveNotificationDto.Request(
                                userId,
                                RecipientType.USER,
                                NotificationType.RESERVATION_CANCELED,
                                "예약이 취소되었습니다",
                                "행사가 취소되어 예약이 자동으로 취소되었습니다.",
                                null,
                                List.of(DeliveryChannel.IN_APP),
                                null
                        ));
                    } catch (Exception e) {
                        log.error("행사 취소 예약 알림 저장 실패. userId={}", userId, e);
                    }
                }
            }
        });
    }
}
