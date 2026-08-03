package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ExpiringReservationRow;
import com.ms.petopia.api.reservation.mapper.ReservationExpirationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReservationExpirationService {

    private final ReservationExpirationMapper expirationMapper;
    private final ReservationTimeProvider timeProvider;

    @Transactional
    public int expireDueReservations(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }

        LocalDateTime now = timeProvider.now();
        List<ExpiringReservationRow> rows =
                expirationMapper.selectDueReservationsForUpdate(now, batchSize);

        int expired = 0;
        for (ExpiringReservationRow row : rows) {
            int updated = expirationMapper.expirePendingReservation(row.getReservationId(), now);
            if (updated == 1) {
                expirationMapper.insertExpiredHistory(row.getReservationId(), now);
                expired++;
            }
        }
        return expired;
    }
}
