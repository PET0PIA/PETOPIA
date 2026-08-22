package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationAvailabilityDateRow;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityFair;
import com.ms.petopia.api.reservation.dto.ReservationAvailabilityResponse;
import com.ms.petopia.api.reservation.mapper.ReservationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReservationAvailabilityServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 1);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @InjectMocks
    private ReservationAvailabilityService service;

    @Test
    void returnsFutureDatesWithRemainingAdvanceReservationCapacity() {
        given(timeProvider.now()).willReturn(NOW);
        given(reservationMapper.selectAvailabilityFair(FAIR_ID)).willReturn(reservableFair());
        given(reservationMapper.selectAvailabilityDates(FAIR_ID, TODAY, NOW)).willReturn(List.of(
                dateRow(TODAY.plusDays(1), 100, 65),
                dateRow(TODAY.plusDays(2), 100, 100)
        ));

        ReservationAvailabilityResponse response = service.getAvailability(FAIR_ID);

        assertThat(response.fairId()).isEqualTo(FAIR_ID);
        assertThat(response.reservationFee()).isEqualTo(10_000);
        assertThat(response.dates()).extracting(date -> date.remainingCapacity())
                .containsExactly(35L, 0L);
        assertThat(response.dates()).extracting(date -> date.available())
                .containsExactly(true, false);
        verify(reservationMapper).selectAvailabilityDates(FAIR_ID, TODAY, NOW);
    }

    @Test
    void rejectsFairOutsideReservationPeriod() {
        ReservationAvailabilityFair fair = reservableFair();
        fair.setReservationStartDate(TODAY.plusDays(1));
        given(timeProvider.now()).willReturn(NOW);
        given(reservationMapper.selectAvailabilityFair(FAIR_ID)).willReturn(fair);

        assertThatThrownBy(() -> service.getAvailability(FAIR_ID))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_NOT_OPEN);
    }

    @Test
    void rejectsMissingFair() {
        given(reservationMapper.selectAvailabilityFair(FAIR_ID)).willReturn(null);

        assertThatThrownBy(() -> service.getAvailability(FAIR_ID))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_FAIR_NOT_FOUND);
    }

    private ReservationAvailabilityFair reservableFair() {
        ReservationAvailabilityFair fair = new ReservationAvailabilityFair();
        fair.setFairId(FAIR_ID);
        fair.setReservationFee(10_000);
        fair.setReservationStartDate(TODAY.minusDays(1));
        fair.setReservationEndDate(TODAY.plusDays(3));
        fair.setPublishedAt(LocalDateTime.of(2026, 7, 20, 10, 0));
        return fair;
    }

    private ReservationAvailabilityDateRow dateRow(LocalDate date, int capacity, long occupiedCount) {
        ReservationAvailabilityDateRow row = new ReservationAvailabilityDateRow();
        row.setVisitDate(date);
        row.setEntryStartTime(LocalTime.of(10, 0));
        row.setEntryEndTime(LocalTime.of(18, 0));
        row.setCapacity(capacity);
        row.setOccupiedCount(occupiedCount);
        return row;
    }
}
