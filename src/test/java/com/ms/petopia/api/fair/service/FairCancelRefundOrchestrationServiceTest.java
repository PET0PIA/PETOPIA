package com.ms.petopia.api.fair.service;

import com.ms.petopia.api.fair.dto.Fair;
import com.ms.petopia.api.fair.mapper.FairCancelRefundMapper;
import com.ms.petopia.api.fair.mapper.FairMapper;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FairCancelRefundOrchestrationServiceTest {

    private static final Long FAIR_ID = 10L;
    private static final Long ACTOR_ID = 99L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 8, 7, 10, 0);

    @Mock
    private FairCancelRefundMapper cancelRefundMapper;

    @Mock
    private FairMapper fairMapper;

    @Mock
    private RefundService refundService;

    @InjectMocks
    private FairCancelRefundOrchestrationService orchestrationService;

    @Test
    @DisplayName("취소된 행사의 예약금/참가비 결제를 각각 알맞은 사유로 환불하고 성공 건수를 반환한다")
    void refundForCanceledFair_결제유형별로_알맞은사유로_환불한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(canceledFair());
        given(cancelRefundMapper.selectRefundablePaymentsByFairId(FAIR_ID)).willReturn(List.of(
                paymentRow(1L, "RESERVATION_DEPOSIT"),
                paymentRow(2L, "VENDOR_FEE")
        ));
        given(refundService.refund(any(), any(), any())).willReturn(null);

        int refunded = orchestrationService.refundForCanceledFair(FAIR_ID, ACTOR_ID);

        assertThat(refunded).isEqualTo(2);
        ArgumentCaptor<RefundRequest> requestCaptor = ArgumentCaptor.forClass(RefundRequest.class);
        verify(refundService).refund(eq(1L), eq(ACTOR_ID), requestCaptor.capture());
        assertThat(requestCaptor.getValue().refundReason()).isEqualTo(RefundReason.FAIR_CANCEL_USER);
        assertThat(requestCaptor.getValue().requestedByDomain()).isEqualTo(RequestedByDomain.FAIR);

        verify(refundService).refund(eq(2L), eq(ACTOR_ID), requestCaptor.capture());
        assertThat(requestCaptor.getValue().refundReason()).isEqualTo(RefundReason.FAIR_CANCEL_VENDOR);
    }

    @Test
    @DisplayName("취소되지 않은 행사면 환불을 내보내지 않고 0을 반환한다")
    void refundForCanceledFair_취소안됐으면_아무것도안한다() {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setCanceledAt(null);
        given(fairMapper.selectById(FAIR_ID)).willReturn(fair);

        int refunded = orchestrationService.refundForCanceledFair(FAIR_ID, ACTOR_ID);

        assertThat(refunded).isZero();
        verify(cancelRefundMapper, never()).selectRefundablePaymentsByFairId(any());
        verify(refundService, never()).refund(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 행사면 0을 반환한다")
    void refundForCanceledFair_행사없으면_0을반환한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(null);

        int refunded = orchestrationService.refundForCanceledFair(FAIR_ID, ACTOR_ID);

        assertThat(refunded).isZero();
        verify(refundService, never()).refund(any(), any(), any());
    }

    @Test
    @DisplayName("환불 대상이 없으면 0을 반환한다")
    void refundForCanceledFair_환불대상없으면_0을반환한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(canceledFair());
        given(cancelRefundMapper.selectRefundablePaymentsByFairId(FAIR_ID)).willReturn(List.of());

        int refunded = orchestrationService.refundForCanceledFair(FAIR_ID, ACTOR_ID);

        assertThat(refunded).isZero();
        verify(refundService, never()).refund(any(), any(), any());
    }

    /**
     * 한 건이 이미 환불됐거나(동시 처리) 정산 확정으로 거부돼도, 그 건만 건너뛰고
     * 나머지 결제는 계속 환불 처리해야 한다(클래스 문서의 "독립 트랜잭션" 설계 참고).
     */
    @Test
    @DisplayName("일부 결제의 환불이 실패해도 나머지는 계속 처리하고, 성공한 건수만 반환한다")
    void refundForCanceledFair_일부실패해도_나머지는_계속처리한다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(canceledFair());
        given(cancelRefundMapper.selectRefundablePaymentsByFairId(FAIR_ID)).willReturn(List.of(
                paymentRow(1L, "RESERVATION_DEPOSIT"),
                paymentRow(2L, "VENDOR_FEE")
        ));
        willThrow(new CommonException(ErrorCode.REFUND_ALREADY_PROCESSED))
                .given(refundService).refund(eq(1L), any(), any());
        given(refundService.refund(eq(2L), any(), any())).willReturn((RefundResponse) null);

        int refunded = orchestrationService.refundForCanceledFair(FAIR_ID, ACTOR_ID);

        assertThat(refunded).isEqualTo(1);
        verify(refundService, times(2)).refund(any(), any(), any());
    }

    @Test
    @DisplayName("개설비(FAIR_OPENING_FEE) 등 대상 외 결제유형은 건너뛴다")
    void refundForCanceledFair_대상외유형은_건너뛴다() {
        given(fairMapper.selectById(FAIR_ID)).willReturn(canceledFair());
        given(cancelRefundMapper.selectRefundablePaymentsByFairId(FAIR_ID))
                .willReturn(List.of(paymentRow(3L, "FAIR_OPENING_FEE")));

        int refunded = orchestrationService.refundForCanceledFair(FAIR_ID, ACTOR_ID);

        assertThat(refunded).isZero();
        verify(refundService, never()).refund(any(), any(), any());
    }

    private Fair canceledFair() {
        Fair fair = new Fair();
        fair.setFairId(FAIR_ID);
        fair.setCanceledAt(NOW);
        return fair;
    }

    private PaymentRow paymentRow(Long paymentId, String paymentType) {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(paymentId);
        row.setPaymentType(paymentType);
        row.setAmount(10_000L);
        row.setStatus("COMPLETED");
        row.setFairId(FAIR_ID);
        return row;
    }
}
