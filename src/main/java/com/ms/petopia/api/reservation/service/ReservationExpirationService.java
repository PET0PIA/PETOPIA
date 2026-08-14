package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ExpiringReservationRow;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import com.ms.petopia.api.reservation.mapper.ReservationExpirationMapper;
import com.ms.petopia.api.statistics.event.ReservationStatusChangedEvent; // 실시간 통계 확인용
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher; // 실시간 통계 확인용
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet; // 실시간 통계 확인용
import java.util.List;
import java.util.Set; // 실시간 통계 확인용

@Service
@RequiredArgsConstructor
public class ReservationExpirationService {

    private static final String ADVANCE = "ADVANCE";

    private final ReservationExpirationMapper expirationMapper;
    private final ReservationCapacityMapper capacityMapper;
    private final ReservationTimeProvider timeProvider;
    private final ApplicationEventPublisher eventPublisher; // 실시간 통계 확인용

    @Transactional
    public int expireDueReservations(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }

        LocalDateTime now = timeProvider.now();
        List<ExpiringReservationRow> rows =
                expirationMapper.selectDueReservationsForUpdate(now, batchSize);

        int expired = 0;
        Set<Long> changedFairIds = new HashSet<>(); // 실시간 통계 확인용
        for (ExpiringReservationRow row : rows) {
            int updated = expirationMapper.expirePendingReservation(row.getReservationId(), now);
            if (updated == 1) {
                expirationMapper.insertExpiredHistory(row.getReservationId(), now);
                // 결제하지 않아 만료된 좌석을 정원에 돌려준다. 건별로 상태 전이가 성사된
                // 경우에만 반납해야 중복 반납이 생기지 않는다.
                if (ADVANCE.equals(row.getReservationType())) {
                    capacityMapper.release(row.getFairId(), row.getVisitDate());
                }
                changedFairIds.add(row.getFairId()); // 실시간 통계 확인용
                expired++;
            }
        }
        // 배치 특성상 같은 fair의 예약이 여러 건일 수 있으므로 fairId 중복 제거 후 이벤트 발행 // 실시간 통계 확인용
        changedFairIds.forEach(id -> eventPublisher.publishEvent(new ReservationStatusChangedEvent(id))); // 실시간 통계 확인용
        return expired;
    }
}
