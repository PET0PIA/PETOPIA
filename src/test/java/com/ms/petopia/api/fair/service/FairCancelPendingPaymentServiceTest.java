package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.payment.dto.PaymentListResponse;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FairCancelPendingPaymentServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 8, 10, 0);

    @Mock
    private FairMapper fairMapper;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private FairCancelPendingPaymentService pendingPaymentService;

    @Test
    @DisplayName("취소된 행사가 없으면 0을 반환하고 결제 조회 자체를 하지 않는다")
    void 취소된행사_없으면_0을반환한다() {
        given(fairMapper.selectCanceledFairIds(50)).willReturn(List.of());

        int canceled = pendingPaymentService.cancelPendingPayments(50);

        assertThat(canceled).isZero();
        verify(paymentService, never()).getPayments(any(), any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("취소된 행사의 PENDING 예약금·참가비 결제를 모두 취소한다")
    void PENDING_예약금_참가비를_모두취소한다() {
        given(fairMapper.selectCanceledFairIds(50)).willReturn(List.of(FAIR_ID));
        given(paymentService.getPayments(FAIR_ID, null, "RESERVATION_DEPOSIT", "PENDING", 0, 100))
                .willReturn(singlePage(payment(1L, "RESERVATION_DEPOSIT")))
                .willReturn(emptyPage());
        given(paymentService.getPayments(FAIR_ID, null, "VENDOR_FEE", "PENDING", 0, 100))
                .willReturn(singlePage(payment(2L, "VENDOR_FEE")))
                .willReturn(emptyPage());

        int canceled = pendingPaymentService.cancelPendingPayments(50);

        assertThat(canceled).isEqualTo(2);
        verify(paymentService).cancelPayment(1L, "FAIR");
        verify(paymentService).cancelPayment(2L, "FAIR");
    }

    @Test
    @DisplayName("취소하고 나면 같은(0번째) 페이지를 다시 조회해서 남은 PENDING 결제를 마저 취소한다")
    void 취소할수록_줄어드는_결과를_빈결과_나올때까지_반복조회한다() {
        given(fairMapper.selectCanceledFairIds(50)).willReturn(List.of(FAIR_ID));
        given(paymentService.getPayments(FAIR_ID, null, "RESERVATION_DEPOSIT", "PENDING", 0, 100))
                .willReturn(singlePage(payment(1L, "RESERVATION_DEPOSIT")))
                .willReturn(singlePage(payment(2L, "RESERVATION_DEPOSIT")))
                .willReturn(emptyPage());
        given(paymentService.getPayments(FAIR_ID, null, "VENDOR_FEE", "PENDING", 0, 100))
                .willReturn(emptyPage());

        int canceled = pendingPaymentService.cancelPendingPayments(50);

        assertThat(canceled).isEqualTo(2);
        verify(paymentService, times(3))
                .getPayments(FAIR_ID, null, "RESERVATION_DEPOSIT", "PENDING", 0, 100);
    }

    @Test
    @DisplayName("한 페이지가 전부 취소 실패하면 무한루프를 피해 멈추고, 실패 건은 세지 않는다")
    void 전부실패하면_더이상반복하지않는다() {
        given(fairMapper.selectCanceledFairIds(50)).willReturn(List.of(FAIR_ID));
        given(paymentService.getPayments(FAIR_ID, null, "RESERVATION_DEPOSIT", "PENDING", 0, 100))
                .willReturn(singlePage(payment(1L, "RESERVATION_DEPOSIT")));
        given(paymentService.getPayments(FAIR_ID, null, "VENDOR_FEE", "PENDING", 0, 100))
                .willReturn(emptyPage());
        willThrow(new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE))
                .given(paymentService).cancelPayment(1L, "FAIR");

        int canceled = pendingPaymentService.cancelPendingPayments(50);

        assertThat(canceled).isZero();
        verify(paymentService, times(1))
                .getPayments(FAIR_ID, null, "RESERVATION_DEPOSIT", "PENDING", 0, 100);
    }

    @Test
    @DisplayName("한 결제유형 조회 중 예외가 나도 다른 유형·다른 행사는 계속 처리한다")
    void 한유형_실패해도_나머지는_계속처리한다() {
        Long otherFairId = 20L;
        given(fairMapper.selectCanceledFairIds(50)).willReturn(List.of(FAIR_ID, otherFairId));
        given(paymentService.getPayments(eq(FAIR_ID), any(), eq("RESERVATION_DEPOSIT"), eq("PENDING"), anyInt(), anyInt()))
                .willThrow(new IllegalStateException("결제 도메인 일시 장애"));
        given(paymentService.getPayments(eq(FAIR_ID), any(), eq("VENDOR_FEE"), eq("PENDING"), anyInt(), anyInt()))
                .willReturn(emptyPage());
        given(paymentService.getPayments(eq(otherFairId), any(), any(), any(), anyInt(), anyInt()))
                .willReturn(singlePage(payment(3L, "VENDOR_FEE")))
                .willReturn(emptyPage());

        int canceled = pendingPaymentService.cancelPendingPayments(50);

        assertThat(canceled).isEqualTo(1);
        verify(paymentService).cancelPayment(3L, "FAIR");
    }

    private PaymentResponse payment(Long paymentId, String paymentType) {
        return new PaymentResponse(
                paymentId, "PAYMENT_" + paymentId, paymentType, 10_000L, "PENDING",
                "MOCK", null, NOW.minusMinutes(5), FAIR_ID, null, 1L, null, null
        );
    }

    private PaymentListResponse singlePage(PaymentResponse... payments) {
        return new PaymentListResponse(List.of(payments), 0, 100, payments.length, 1);
    }

    private PaymentListResponse emptyPage() {
        return new PaymentListResponse(List.of(), 0, 100, 0, 0);
    }
}
