package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationListResponse;
import com.ms.petopia.api.reservation.dto.ReservationListRow;
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
class ReservationQueryServiceTest {

    @Mock
    private ReservationMapper reservationMapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @InjectMocks
    private ReservationQueryService service;

    @Test
    void returnsOnlyMapperResultsAndMarksUsableQrAndPaymentStatuses() {
        given(reservationMapper.countMyReservations(20L)).willReturn(3L);
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 1, 9, 0));
        given(reservationMapper.selectMyReservations(20L, 0, 20)).willReturn(List.of(
                row(30L, "CONFIRMED", null, null),
                row(31L, "CHECKED_IN", null, LocalDateTime.of(2026, 8, 2, 10, 5)),
                row(32L, "PENDING_PAYMENT", LocalDateTime.of(2026, 8, 1, 9, 10), null)
        ));

        ReservationListResponse response = service.getMyReservations(20L, 0, 20);

        assertThat(response.items()).hasSize(3);
        assertThat(response.items()).extracting(item -> item.qrAvailable())
                .containsExactly(true, true, false);
        assertThat(response.items()).extracting(item -> item.paymentAvailable())
                .containsExactly(false, false, true);
        assertThat(response.items().get(1).checkedInAt()).isEqualTo(LocalDateTime.of(2026, 8, 2, 10, 5));
        assertThat(response.totalElements()).isEqualTo(3);
        assertThat(response.totalPages()).isEqualTo(1);
        assertThat(response.hasNext()).isFalse();
        verify(reservationMapper).selectMyReservations(20L, 0, 20);
    }

    @Test
    void marksReservationAsEndedAfterEntryEndTime() {
        given(reservationMapper.countMyReservations(20L)).willReturn(1L);
        given(timeProvider.now()).willReturn(LocalDateTime.of(2026, 8, 2, 18, 0, 1));
        given(reservationMapper.selectMyReservations(20L, 0, 20)).willReturn(List.of(
                row(30L, "CONFIRMED", null, null)
        ));

        ReservationListResponse response = service.getMyReservations(20L, 0, 20);

        assertThat(response.items().getFirst().isEnded()).isTrue();
        assertThat(response.items().getFirst().qrAvailable()).isFalse();
    }

    @Test
    void rejectsInvalidUserId() {
        assertThatThrownBy(() -> service.getMyReservations(0L, 0, 20))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void doesNotAllowPaymentResumeAtExactDeadline() {
        given(reservationMapper.countMyReservations(20L)).willReturn(1L);
        LocalDateTime deadline = LocalDateTime.of(2026, 8, 1, 9, 0);
        given(timeProvider.now()).willReturn(deadline);
        given(reservationMapper.selectMyReservations(20L, 0, 20)).willReturn(List.of(
                row(30L, "PENDING_PAYMENT", deadline, null)
        ));

        ReservationListResponse response = service.getMyReservations(20L, 0, 20);

        assertThat(response.items().getFirst().paymentAvailable()).isFalse();
    }

    private ReservationListRow row(
            Long reservationId,
            String status,
            LocalDateTime paymentExpiresAt,
            LocalDateTime checkedInAt
    ) {
        ReservationListRow row = new ReservationListRow();
        row.setReservationId(reservationId);
        row.setFairName("서울 펫페어");
        row.setVisitDate(LocalDate.of(2026, 8, 2));
        row.setEntryStartTime(LocalTime.of(10, 0));
        row.setEntryEndTime(LocalTime.of(18, 0));
        row.setReservationStatus(status);
        row.setAmount(10_000);
        row.setReservedAt(LocalDateTime.of(2026, 8, 1, 9, 0));
        row.setPaymentExpiresAt(paymentExpiresAt);
        row.setCheckedInAt(checkedInAt);
        return row;
    }
}
