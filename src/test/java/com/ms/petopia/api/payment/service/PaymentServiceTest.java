package com.ms.petopia.api.payment.service;


import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.dto.PaymentRow;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;


//JUnit5 확장기능,자동으로  Mock초기화,없으면 Mock선언 필드 null처리됨
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    //가짜(mock) 객체 ,실제로 DB에 안 붙음.
    @Mock
    private PaymentMapper paymentMapper;

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
                row.getPaymentId(), row.getPaymentType(), row.getAmount(), row.getStatus(), row.getMethod(),
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
        PaymentResponse result = paymentService.payVendorFee(40L,request);

        // Assert: 응답에 요청값·기본값(COMPLETED/MOCK)이 제대로 들어갔는지
        assertThat(result.paymentType()).isEqualTo("VENDOR_FEE");
        assertThat(result.amount()).isEqualTo(50000L);
        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.method()).isEqualTo("MOCK");
        assertThat(result.fairId()).isEqualTo(10L);
        assertThat(result.businessId()).isEqualTo(20L);
        assertThat(result.applicationId()).isEqualTo(40L);
        assertThat(result.paidAt()).isNotNull();

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
        assertThatThrownBy(() -> paymentService.payVendorFee(40L, request))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);

    }


}


