package com.ms.petopia.api.refund.service;

import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundMapper refundMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private RefundService refundService;

    // COMPLETED 상태의 결제 하나를 미리 만들어두는 헬퍼. 환불 테스트들이 전부
    // "완료된 결제가 이미 있다"는 상황에서 시작하므로 중복을 줄이려고 뺐음.
    private PaymentRow completedPaymentRow() {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(1L);
        row.setPaymentType("VENDOR_FEE");
        row.setAmount(50000L);
        row.setStatus("COMPLETED");
        row.setMethod("TOSS");
        row.setFairId(10L);
        row.setBusinessId(20L);
        row.setApplicationId(40L);
        return row;
    }

    @Test
    @DisplayName("완료된 결제를 환불하면 결제 전액이 즉시 COMPLETED로 처리된다")
    void refund_성공() {
        // Arrange
        given(paymentMapper.selectById(1L)).willReturn(completedPaymentRow());
        RefundRequest request = new RefundRequest("USER_CANCEL", "RESERVATION");

        // Act
        RefundResponse result = refundService.refund(1L, 99L, request);

        // Assert: 모의 환불이라 REQUESTED 같은 중간 상태 없이 바로 COMPLETED로,
        // 금액은 클라이언트가 안 보내고 결제 전액(MVP 전액환불 고정)을 그대로 씀
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.refundAmount()).isEqualTo(50000L);
        assertThat(result.refundReason()).isEqualTo("USER_CANCEL");
        assertThat(result.requestedByDomain()).isEqualTo("RESERVATION");
        assertThat(result.processedAt()).isNotNull();

        verify(refundMapper).insert(argThat(row -> row.getPaymentId().equals(1L)
                && "COMPLETED".equals(row.getStatus())));
    }

    @Test
    @DisplayName("존재하지 않는 결제를 환불하려 하면 예외를 던진다")
    void refund_결제없음_예외를던진다() {
        given(paymentMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> refundService.refund(999L, 99L, new RefundRequest("USER_CANCEL", "RESERVATION")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("결제가 완료 상태가 아니면 환불할 수 없다")
    void refund_결제완료상태아님_예외를던진다() {
        PaymentRow row = completedPaymentRow();
        row.setStatus("PENDING");
        given(paymentMapper.selectById(1L)).willReturn(row);

        assertThatThrownBy(() -> refundService.refund(1L, 99L, new RefundRequest("USER_CANCEL", "RESERVATION")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE);
    }

    @Test
    @DisplayName("이미 환불이 접수된 결제를 다시 환불하려 하면 예외를 던진다")
    void refund_이미환불됨_예외를던진다() {
        // Arrange: 실제로는 DB의 UK_REFUND_PAYMENT 위반이 DuplicateKeyException으로 올라옴
        given(paymentMapper.selectById(1L)).willReturn(completedPaymentRow());
        willThrow(new DuplicateKeyException("refund payment unique violation"))
                .given(refundMapper).insert(any(RefundRow.class));

        assertThatThrownBy(() -> refundService.refund(1L, 99L, new RefundRequest("USER_CANCEL", "RESERVATION")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("존재하는 환불ID로 조회하면 환불 상세를 반환한다")
    void getRefund_존재하는환불_상세를반환한다() {
        RefundRow row = new RefundRow();
        row.setRefundId(1L);
        row.setPaymentId(1L);
        row.setRefundReason("USER_CANCEL");
        row.setRequestedByDomain("RESERVATION");
        row.setRefundAmount(50000L);
        row.setStatus("COMPLETED");
        row.setRequestedAt(LocalDateTime.now());
        row.setProcessedAt(LocalDateTime.now());
        given(refundMapper.selectById(1L)).willReturn(row);

        RefundResponse result = refundService.getRefund(1L);

        assertThat(result.refundId()).isEqualTo(1L);
        assertThat(result.paymentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("존재하지 않는 환불ID로 조회하면 예외를 던진다")
    void getRefund_존재하지않음_예외를던진다() {
        given(refundMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> refundService.getRefund(999L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_NOT_FOUND);
    }

    @Test
    @DisplayName("환불된 적 없는 결제ID로 조회하면 null을 반환한다")
    void findByPaymentId_환불없음_null반환() {
        given(refundMapper.selectByPaymentId(1L)).willReturn(null);

        assertThat(refundService.findByPaymentId(1L)).isNull();
    }
}
