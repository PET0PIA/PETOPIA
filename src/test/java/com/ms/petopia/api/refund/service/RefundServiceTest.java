package com.ms.petopia.api.refund.service;

import com.ms.petopia.api.notification.dto.NotificationType;
import com.ms.petopia.api.notification.dto.SaveNotificationDto;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.refund.dto.RefundReason;
import com.ms.petopia.api.refund.dto.RefundRequest;
import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.dto.RequestedByDomain;
import com.ms.petopia.api.refund.mapper.RefundMapper;
import com.ms.petopia.api.settlement.dto.SettlementItemRow;
import com.ms.petopia.api.settlement.mapper.SettlementMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundMapper refundMapper;

    @Mock
    private PaymentMapper paymentMapper;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private RefundService refundService;

    private static final RefundRequest USER_CANCEL_REQUEST =
            new RefundRequest(RefundReason.USER_CANCEL, RequestedByDomain.RESERVATION);

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
        row.setPayerUserId(90L);
        return row;
    }

    @Test
    @DisplayName("완료된 결제를 환불하면 결제 전액이 즉시 COMPLETED로 처리된다")
    void refund_성공() {
        // Arrange
        given(paymentMapper.selectByIdForUpdate(1L)).willReturn(completedPaymentRow());

        // Act
        RefundResponse result = refundService.refund(1L, 99L, USER_CANCEL_REQUEST);

        // Assert: 모의 환불이라 REQUESTED 같은 중간 상태 없이 바로 COMPLETED로,
        // 금액은 클라이언트가 안 보내고 결제 전액(MVP 전액환불 고정)을 그대로 씀
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.refundAmount()).isEqualTo(50000L);
        assertThat(result.refundReason()).isEqualTo("USER_CANCEL");
        assertThat(result.requestedByDomain()).isEqualTo("RESERVATION");
        assertThat(result.processedAt()).isNotNull();

        // 환불 완료 알림(IN_APP)이 결제자 앞으로 저장 요청됐는지
        verify(notificationService).save(argThat(req -> req.userId().equals(90L)
                && req.type() == NotificationType.REFUND_COMPLETED));

        verify(refundMapper).insert(argThat(row -> row.getPaymentId().equals(1L)
                && "COMPLETED".equals(row.getStatus())));
    }

    @Test
    @DisplayName("이미 정산에 포함된 결제는 환불할 수 없다")
    void refund_이미정산됨_예외를던진다() {
        // Arrange: 이 결제가 이미 어느 정산의 SETTLEMENT_ITEM으로 들어가 있는 상황
        // (정산 계산 이후 환불을 허용하면 정산 금액이 옛날 값으로 굳어버리는 걸 방지하는 방어 로직)
        given(paymentMapper.selectByIdForUpdate(1L)).willReturn(completedPaymentRow());
        SettlementItemRow item = new SettlementItemRow();
        item.setSettlementItemId(1L);
        item.setSettlementId(5L);
        item.setPaymentId(1L);
        item.setAmountIncluded(50000L);
        given(settlementMapper.selectItemByPaymentId(1L)).willReturn(item);

        assertThatThrownBy(() -> refundService.refund(1L, 99L, USER_CANCEL_REQUEST))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE);

        // 정산 위반으로 걸렸으면 환불 row 자체를 만들면 안 됨
        verify(refundMapper, never()).insert(any(RefundRow.class));
    }

    @Test
    @DisplayName("환불 완료 알림 저장이 실패해도 환불 응답 자체는 성공으로 반환한다")
    void refund_알림저장실패해도_환불응답은성공이다() {
        // Arrange: 환불 자체는 이미 성공했는데 알림함 저장만 터지는 상황(예: 알림 도메인 장애)
        given(paymentMapper.selectByIdForUpdate(1L)).willReturn(completedPaymentRow());
        willThrow(new RuntimeException("notification save failed"))
                .given(notificationService).save(any(SaveNotificationDto.Request.class));

        // Act & Assert: 예외가 밖으로 안 새고 환불은 정상 COMPLETED로 반환돼야 함
        RefundResponse result = refundService.refund(1L, 99L, USER_CANCEL_REQUEST);
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("예약금 결제를 환불하면 응답에 예약ID가 같이 담겨서 예약 도메인이 바로 알 수 있다")
    void refund_예약금결제_응답에예약ID포함() {
        // Arrange: 예약 도메인이 이 API를 동기 호출하고 응답만으로 어떤 예약 건인지 알아야 하는 흐름
        PaymentRow row = completedPaymentRow();
        row.setPaymentType("RESERVATION_DEPOSIT");
        row.setReservationId(500L);
        given(paymentMapper.selectByIdForUpdate(1L)).willReturn(row);

        RefundResponse result = refundService.refund(1L, 99L, USER_CANCEL_REQUEST);

        assertThat(result.reservationId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("존재하지 않는 결제를 환불하려 하면 예외를 던진다")
    void refund_결제없음_예외를던진다() {
        given(paymentMapper.selectByIdForUpdate(999L)).willReturn(null);

        assertThatThrownBy(() -> refundService.refund(999L, 99L, USER_CANCEL_REQUEST))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("결제가 완료 상태가 아니면 환불할 수 없다")
    void refund_결제완료상태아님_예외를던진다() {
        PaymentRow row = completedPaymentRow();
        row.setStatus("PENDING");
        given(paymentMapper.selectByIdForUpdate(1L)).willReturn(row);

        assertThatThrownBy(() -> refundService.refund(1L, 99L, USER_CANCEL_REQUEST))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.REFUND_TARGET_NOT_REFUNDABLE);
    }

    @Test
    @DisplayName("이미 환불이 접수된 결제를 다시 환불하려 하면 예외를 던진다")
    void refund_이미환불됨_예외를던진다() {
        // Arrange: 실제로는 DB의 UK_REFUND_PAYMENT 위반이 DuplicateKeyException으로 올라옴
        given(paymentMapper.selectByIdForUpdate(1L)).willReturn(completedPaymentRow());
        willThrow(new DuplicateKeyException("refund payment unique violation"))
                .given(refundMapper).insert(any(RefundRow.class));

        assertThatThrownBy(() -> refundService.refund(1L, 99L, USER_CANCEL_REQUEST))
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
