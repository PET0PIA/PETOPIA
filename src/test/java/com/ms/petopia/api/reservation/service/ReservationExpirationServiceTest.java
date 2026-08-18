package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ExpiringReservationRow;
import com.ms.petopia.api.reservation.mapper.ReservationCapacityMapper;
import com.ms.petopia.api.reservation.mapper.ReservationExpirationMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationExpirationServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 10);

    @Mock
    private ReservationExpirationMapper mapper;
    @Mock
    private ReservationCapacityMapper capacityMapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @Mock
    private ApplicationEventPublisher eventPublisher;
    @InjectMocks
    private ReservationExpirationService service;

    @Test
    void expiresLockedPendingReservationsAndWritesHistory() {
        ExpiringReservationRow row = new ExpiringReservationRow();
        row.setReservationId(10L);
        row.setFairId(FAIR_ID);
        given(timeProvider.now()).willReturn(NOW);
        given(mapper.selectDueReservationsForUpdate(NOW, 200)).willReturn(List.of(row));
        given(mapper.expirePendingReservation(10L, NOW)).willReturn(1);

        assertThat(service.expireDueReservations(200)).isEqualTo(1);
        verify(mapper).insertExpiredHistory(10L, NOW);
    }

    @Test
    void skipsHistoryWhenConcurrentConfirmationAlreadyChangedStatus() {
        ExpiringReservationRow row = new ExpiringReservationRow();
        row.setReservationId(10L);
        row.setFairId(FAIR_ID);
        given(timeProvider.now()).willReturn(NOW);
        given(mapper.selectDueReservationsForUpdate(NOW, 200)).willReturn(List.of(row));
        given(mapper.expirePendingReservation(10L, NOW)).willReturn(0);

        assertThat(service.expireDueReservations(200)).isZero();
        verify(mapper, never()).insertExpiredHistory(10L, NOW);
    }
}
