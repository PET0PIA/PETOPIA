package com.ms.petopia.api.payment.service;


import com.ms.petopia.api.payment.client.TossPaymentClient;
import com.ms.petopia.api.payment.dto.ConfirmPaymentRequest;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.dto.TossPaymentResponse;
import com.ms.petopia.api.payment.dto.VendorFeePaymentRequest;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
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
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;


//JUnit5 확장기능,자동으로  Mock초기화,없으면 Mock선언 필드 null처리됨
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    //가짜(mock) 객체 ,실제로 DB에 안 붙음.
    @Mock
    private PaymentMapper paymentMapper;

    // 토스 API를 실제로 호출하지 않도록 가짜로 대체. confirmPayment 테스트에서
    // "토스가 이렇게 응답했다고 치자"를 흉내내는 데 씀.
    @Mock
    private TossPaymentClient tossPaymentClient;

    // PaymentService 생성자가 PaymentMapper를 받는 구조여야 동작함
    // (@RequiredArgsConstructor 패턴).
    @InjectMocks
    private PaymentService paymentService;

    @Test
    // 테스트 리포트/IDE에 표시될 설명.
    @DisplayName("존재하는 결제ID를 조회하면 결제 상세를 반환한다")
    void getPayment_존재하는결제_결제상세를반환한다() {

        // Arrange: 이제 매퍼가 리턴하는 건 PaymentResponse(record)가 아니라
        // PaymentRow(세터 있는 mutable 클래스, DB 로우 그대로 담는 용도).
        PaymentRow row = new PaymentRow();
        row.setPaymentId(1L);
        row.setPaymentType("VENDOR_FEE");
        row.setAmount(50000L);
        row.setStatus("COMPLETED");
        row.setMethod("MOCK");
        row.setPaidAt(LocalDateTime.now());
        row.setCreatedAt(LocalDateTime.now());
        row.setFairId(10L);
        row.setBusinessId(20L);
        row.setPayerUserId(30L);
        row.setReservationId(null);
        row.setApplicationId(40L);
        given(paymentMapper.selectById(1L)).willReturn(row);

        // row에서 값을 그대로 가져와서 expected를 만듦 (따로 값을 또 타이핑하면
        // paidAt/createdAt 같은 시간값이 미묘하게 달라질 수 있어서, row 기준으로 통일).
        PaymentResponse expected = new PaymentResponse(
                row.getPaymentId(), "PAYMENT_"+ row.getPaymentId(), row.getPaymentType(), row.getAmount(), row.getStatus(), row.getMethod(),
                row.getPaidAt(), row.getCreatedAt(), row.getFairId(), row.getBusinessId(),
                row.getPayerUserId(), row.getReservationId(), row.getApplicationId()
        );

        // Act
        PaymentResponse result = paymentService.getPayment(1L);

        // Assert: 서비스가 PaymentRow → PaymentResponse 변환까지 제대로 했는지 검증하는 셈.
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("존재하지 않는 결제ID를 조회하면 예외를 던진다")
    void getPayment_존재하지않는결제_예외를던진다() {
        //Arrange: 999L로 조회하면 매퍼가 null을 리턴하는 상황(=DB에 없는 상황)을 흉내냄.
        given(paymentMapper.selectById(999L)).willReturn(null);

        // Act & Assert: 예외를 던지는 케이스는 Act와 Assert를 분리하기 어려워서 보통 합침.
        // assertThatThrownBy: 람다 안의 코드를 실행하다가 예외가 터지면 그 예외를 캡처해서
        // 이후 체이닝으로 검증할 수 있게 해줌.
        assertThatThrownBy(() -> paymentService.getPayment(999L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("참가비 결제를 요청하면 결제가 생성된다")
    void payVendorFee_결제생성_성공() {
        // Arrange: application 테이블을 안 보니까, 프론트가 금액/fairId/businessId를 실어서 보낸 상황 흉내
        VendorFeePaymentRequest request = new VendorFeePaymentRequest(10L,20L, 50000L);

        //Act
        PaymentResponse result = paymentService.payVendorFee(40L,90L,request);

        // Assert: 응답에 요청값·기본값(COMPLETED/MOCK)이 제대로 들어갔는지
        assertThat(result.paymentType()).isEqualTo("VENDOR_FEE");
        assertThat(result.amount()).isEqualTo(50000L);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.method()).isEqualTo("TOSS");
        assertThat(result.fairId()).isEqualTo(10L);
        assertThat(result.businessId()).isEqualTo(20L);
        assertThat(result.applicationId()).isEqualTo(40L);
        assertThat(result.paidAt()).isNull();

        // insert가 실제로 호출됐는지 + idempotencyKey가 applicationId 기준으로
        // 만들어졌는지 확인 (이게 나중에 중복결제를 막아주는 값이라 제대로 세팅되는지가 중요)
        verify(paymentMapper).insert(argThat(row -> "VENDOR_FEE_40".equals(row.getIdempotencyKey())));

    }

    @Test
    @DisplayName("이미 결제된 참가신청에 다시 결제를 요청하면 예외를 던진다")
    void payVendorFee_중복결제_예외를던진다() {

        // Arrange: 실제로는 DB의 idempotency_key UNIQUE 제약 위반이
        // DuplicateKeyException으로 올라옴 — 여기선 insert 호출 시 그 예외를 던지도록
        // 미리 세팅해서 "이미 결제된 상황"을 흉내냄.
        VendorFeePaymentRequest request = new VendorFeePaymentRequest(10L,20L,50000L);
        willThrow(new DuplicateKeyException("idempotency key violation"))
                .given(paymentMapper).insert(any(PaymentRow.class));

        // Act & Assert
        assertThatThrownBy(() -> paymentService.payVendorFee(40L, 90L, request))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);

    }

    // PENDING 상태의 결제 하나를 미리 만들어두는 헬퍼. confirmPayment 테스트들이
    // 전부 "PENDING인 결제가 이미 있다"는 상황에서 시작하므로 중복을 줄이려고 뺐음.
    private PaymentRow pendingRow() {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(1L);
        row.setPaymentType("VENDOR_FEE");
        row.setAmount(50000L);
        row.setStatus("PENDING");
        row.setMethod("TOSS");
        row.setPayerUserId(90L);
        row.setFairId(10L);
        row.setBusinessId(20L);
        row.setApplicationId(40L);
        return row;
    }

    @Test
    @DisplayName("결제 승인을 요청하면 토스 승인 확인 후 COMPLETED로 바뀐다")
    void confirmPayment_성공() {
        // Arrange
        PaymentRow row = pendingRow();
        given(paymentMapper.selectById(1L)).willReturn(row);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_1"), eq(50000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_1", "DONE", 50000L, "카드", OffsetDateTime.now()
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);

        // Act
        PaymentResponse result = paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123"));

        // Assert
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.method()).isEqualTo("카드");
        assertThat(result.paidAt()).isNotNull();
        verify(paymentMapper).markCompleted(any(PaymentRow.class));
    }

    @Test
    @DisplayName("존재하지 않는 결제를 승인하려 하면 예외를 던진다")
    void confirmPayment_결제없음_예외를던진다() {
        given(paymentMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> paymentService.confirmPayment(999L, 90L, new ConfirmPaymentRequest("paymentKey123")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("결제자 본인이 아닌 사용자가 승인하려 하면 예외를 던진다")
    void confirmPayment_소유자아님_예외를던진다() {
        // Arrange: pendingRow()는 payerUserId=90L인데, 다른 사람(999L)이 승인 시도하는 상황
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());

        assertThatThrownBy(() -> paymentService.confirmPayment(1L, 999L, new ConfirmPaymentRequest("paymentKey123")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("이미 완료되었거나 취소된 결제를 다시 승인하려 하면 예외를 던진다")
    void confirmPayment_PENDING아님_예외를던진다() {
        PaymentRow row = pendingRow();
        row.setStatus("COMPLETED");
        given(paymentMapper.selectById(1L)).willReturn(row);

        assertThatThrownBy(() -> paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
    }

    @Test
    @DisplayName("동시에 두 번 승인 처리돼 markCompleted가 0건이면 예외를 던진다")
    void confirmPayment_동시승인_예외를던진다() {
        // Arrange: 토스 승인까지는 성공했는데, 그 사이 다른 요청이 먼저 COMPLETED로
        // 바꿔놔서 markCompleted의 WHERE status='PENDING' 조건에 안 걸리는 상황(0건 업데이트)
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        given(tossPaymentClient.confirmPayment(any(), any(), any()))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_1", "DONE", 50000L, "카드", OffsetDateTime.now()
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(0);

        assertThatThrownBy(() -> paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
    }

    @Test
    @DisplayName("토스가 승인을 확정적으로 거부하면(4xx) 결제를 FAILED로 남긴다")
    void confirmPayment_토스승인거부_FAILED로전이한다() {
        // Arrange: 토스 클라이언트가 4xx를 이미 PAYMENT_APPROVAL_FAILED로 변환해서 던지는 상황을 흉내냄
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        willThrow(new CommonException(ErrorCode.PAYMENT_APPROVAL_FAILED))
                .given(tossPaymentClient).confirmPayment(any(), any(), any());

        // Act & Assert: 호출한 쪽에는 여전히 실패 예외가 그대로 전달돼야 함
        assertThatThrownBy(() -> paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_APPROVAL_FAILED);

        // 예외를 던지면서도 DB엔 FAILED로 남겨야 함(트랜잭션 롤백에 안 딸려가는지 확인하는 셈)
        verify(paymentMapper).markFailed(eq(1L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("토스 서버 장애(5xx)면 결제 상태를 건드리지 않고 예외만 전달한다")
    void confirmPayment_토스서버장애_상태유지() {
        // Arrange: 5xx는 실제로 승인됐을 수도 있어서 실패로 단정하면 안 됨(PENDING 유지)
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        willThrow(new CommonException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE))
                .given(tossPaymentClient).confirmPayment(any(), any(), any());

        assertThatThrownBy(() -> paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);

        // markFailed/markCompleted 둘 다 호출되면 안 됨 — 상태는 그대로 PENDING
        verify(paymentMapper, never()).markFailed(any(), any());
        verify(paymentMapper, never()).markCompleted(any());
    }

}


