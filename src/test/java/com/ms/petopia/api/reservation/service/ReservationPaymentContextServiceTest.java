package com.ms.petopia.api.reservation.service;

import com.ms.petopia.api.reservation.dto.ReservationPaymentContextResponse;
import com.ms.petopia.api.reservation.dto.ReservationPaymentContextRow;
import com.ms.petopia.api.reservation.mapper.ReservationPaymentConfirmationMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReservationPaymentContextServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 1, 10, 0);

    @Mock
    private ReservationPaymentConfirmationMapper mapper;
    @Mock
    private ReservationTimeProvider timeProvider;
    @InjectMocks
    private ReservationPaymentContextService service;

    @Test
    void exposesOnlyServerOwnedPayableValues() {
        ReservationPaymentContextRow row = pendingContext();
        given(mapper.selectPaymentContext(10L)).willReturn(row);
        given(timeProvider.now()).willReturn(NOW);

        ReservationPaymentContextResponse response = service.getPayableContext(10L);

        assertThat(response.amount()).isEqualTo(15_000);
        assertThat(response.payerUserId()).isEqualTo(20L);
        assertThat(response.paymentExpiresAt()).isEqualTo(NOW.plusMinutes(10));
    }

    @Test
    void rejectsContextAtExactDeadline() {
        ReservationPaymentContextRow row = pendingContext();
        row.setPaymentExpiresAt(NOW);
        given(mapper.selectPaymentContext(10L)).willReturn(row);
        given(timeProvider.now()).willReturn(NOW);

        assertThatThrownBy(() -> service.getPayableContext(10L))
                .isInstanceOf(CommonException.class)
                .extracting(exception -> ((CommonException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESERVATION_PAYMENT_EXPIRED);
    }

    private ReservationPaymentContextRow pendingContext() {
        ReservationPaymentContextRow row = new ReservationPaymentContextRow();
        row.setReservationId(10L);
        row.setFairId(30L);
        row.setUserId(20L);
        row.setReservationType("ONSITE_DIRECT");
        row.setStatus("PENDING_PAYMENT");
        row.setReservationAmount(15_000);
        row.setPaymentExpiresAt(NOW.plusMinutes(10));
        return row;
    }
}
