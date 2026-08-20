package com.ms.petopia.api.payment.service;


import com.ms.petopia.api.application.service.ApplicationService;
import com.ms.petopia.api.audit.service.AuditLogService;
import com.ms.petopia.api.fair.service.FairAdminAccessGuard;
import com.ms.petopia.api.payment.client.FairOpeningFeePaymentContractClient;
import com.ms.petopia.api.payment.client.ReservationPaymentContractClient;
import com.ms.petopia.api.payment.client.TossPaymentClient;
import com.ms.petopia.api.payment.dto.ConfirmPaymentRequest;
import com.ms.petopia.api.payment.dto.FairOpeningFeePaymentContext;
import com.ms.petopia.api.payment.dto.PaymentListResponse;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.dto.PaymentRow;
import com.ms.petopia.api.payment.dto.ReservationPaymentCompletionResult;
import com.ms.petopia.api.payment.dto.ReservationPaymentContext;
import com.ms.petopia.api.payment.dto.TossPaymentResponse;
import com.ms.petopia.api.payment.dto.TossWebhookEvent;
import com.ms.petopia.api.payment.dto.VendorFeePaymentRequest;
import com.ms.petopia.api.notification.service.NotificationService;
import com.ms.petopia.api.payment.mapper.PaymentMapper;
import com.ms.petopia.api.refund.dto.RefundReason;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

    // 예약 도메인 내부 계약 API를 실제로 호출하지 않도록 가짜로 대체.
    // payReservationDeposit(컨텍스트 조회)와 confirmPayment(완료 통지) 양쪽 테스트에서 씀.
    @Mock
    private ReservationPaymentContractClient reservationPaymentContractClient;

    // 행사 도메인 내부 계약 API를 실제로 호출하지 않도록 가짜로 대체.
    // payFairOpeningFee(컨텍스트 조회)에서 씀.
    @Mock
    private FairOpeningFeePaymentContractClient fairOpeningFeePaymentContractClient;

    @Mock
    private NotificationService notificationService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ApplicationService applicationService;

    @Mock
    private RefundService refundService;

    // getPayment(paymentId, userId) 오버로드가 소유자 아닐 때 확인하는 행사담당자 가드.
    // Mock이라 기본적으로 아무것도 안 하므로(void 메서드 기본 no-op), 거부 케이스만
    // willThrow로 스텁한다.
    @Mock
    private FairAdminAccessGuard fairAdminAccessGuard;

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
        row.setOrderId("PAYMENT_1");
        given(paymentMapper.selectById(1L)).willReturn(row);

        // row에서 값을 그대로 가져와서 expected를 만듦 (따로 값을 또 타이핑하면
        // paidAt/createdAt 같은 시간값이 미묘하게 달라질 수 있어서, row 기준으로 통일).
        // PaymentResponse.from(row)를 그대로 써서, 나중에 필드가 추가돼도(예: 간편결제
        // 제공사·가상계좌 정보) 이 테스트를 손댈 필요가 없게 한다.
        PaymentResponse expected = PaymentResponse.from(row);

        // Act
        PaymentResponse result = paymentService.getPayment(1L);

        // Assert: 서비스가 PaymentRow → PaymentResponse 변환까지 제대로 했는지 검증하는 셈.
        assertThat(result).isEqualTo(expected);
    }

    @Test
    @DisplayName("예약ID로 조회하면 그 예약의 예약금 결제 상세를 반환한다")
    void getByReservationId_존재하는예약_결제상세를반환한다() {
        // Arrange: 예약 도메인이 환불 API를 부르기 전에 paymentId를 알아내는 흐름을 흉내냄
        PaymentRow row = new PaymentRow();
        row.setPaymentId(2L);
        row.setPaymentType("RESERVATION_DEPOSIT");
        row.setAmount(30000L);
        row.setStatus("COMPLETED");
        row.setMethod("TOSS");
        row.setFairId(10L);
        row.setPayerUserId(90L);
        row.setReservationId(500L);
        given(paymentMapper.selectByReservationId(500L)).willReturn(row);

        // Act
        PaymentResponse result = paymentService.getByReservationId(500L);

        // Assert
        assertThat(result.paymentId()).isEqualTo(2L);
        assertThat(result.reservationId()).isEqualTo(500L);
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("결제된 적 없는 예약ID로 조회하면 예외를 던진다")
    void getByReservationId_결제없음_예외를던진다() {
        given(paymentMapper.selectByReservationId(999L)).willReturn(null);

        assertThatThrownBy(() -> paymentService.getByReservationId(999L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("참가신청ID로 조회하면 그 신청의 참가비 결제 상세를 반환한다")
    void findByApplicationId_존재하는결제_결제상세를반환한다() {
        // Arrange: 채린님(참가업체) 도메인이 취소승인 처리 중 환불 대상을 찾는 흐름을 흉내냄
        PaymentRow row = new PaymentRow();
        row.setPaymentId(5L);
        row.setPaymentType("VENDOR_FEE");
        row.setAmount(70000L);
        row.setStatus("COMPLETED");
        row.setFairId(10L);
        row.setBusinessId(20L);
        row.setApplicationId(40L);
        given(paymentMapper.selectByApplicationId(40L)).willReturn(row);

        // Act
        PaymentResponse result = paymentService.findByApplicationId(40L);

        // Assert
        assertThat(result.paymentId()).isEqualTo(5L);
        assertThat(result.applicationId()).isEqualTo(40L);
        assertThat(result.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("결제가 아직 없는 참가신청을 조회하면 예외 대신 null을 반환한다")
    void findByApplicationId_결제없음_null을반환한다() {
        // Arrange: 취소승인이 결제 전(신청만 하고 아직 결제 시작 안 한 상태)에도 가능한 케이스 —
        // getByReservationId와 달리 여기선 "결제 없음"이 정상 상황이라 예외를 던지면 안 된다.
        given(paymentMapper.selectByApplicationId(999L)).willReturn(null);

        PaymentResponse result = paymentService.findByApplicationId(999L);

        assertThat(result).isNull();
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
    @DisplayName("결제 소유자가 조회하면 행사담당자 검증 없이 결제 상세를 반환한다")
    void getPayment_소유자가조회_결제상세를반환한다() {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(1L);
        row.setPaymentType("VENDOR_FEE");
        row.setStatus("COMPLETED");
        row.setFairId(10L);
        row.setPayerUserId(30L);
        given(paymentMapper.selectById(1L)).willReturn(row);

        PaymentResponse result = paymentService.getPayment(1L, 30L);

        assertThat(result.paymentId()).isEqualTo(1L);
        // 소유자면 굳이 행사담당자인지까지 확인할 필요 없다.
        verify(fairAdminAccessGuard, never()).checkAssigned(any());
    }

    @Test
    @DisplayName("소유자가 아니면 그 행사 담당 관리자인지 확인한다")
    void getPayment_소유자아님_행사담당자면조회허용() {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(1L);
        row.setPaymentType("VENDOR_FEE");
        row.setStatus("COMPLETED");
        row.setFairId(10L);
        row.setPayerUserId(30L);
        given(paymentMapper.selectById(1L)).willReturn(row);
        // fairAdminAccessGuard.checkAssigned(10L)이 Mock 기본 no-op이니 "담당자로 확인됨"을 흉내냄

        PaymentResponse result = paymentService.getPayment(1L, 99L);

        assertThat(result.paymentId()).isEqualTo(1L);
        verify(fairAdminAccessGuard).checkAssigned(10L);
    }

    @Test
    @DisplayName("소유자도, 행사 담당 관리자도 아니면 예외를 던진다")
    void getPayment_소유자아님_관리자도아님_예외를던진다() {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(1L);
        row.setPaymentType("VENDOR_FEE");
        row.setStatus("COMPLETED");
        row.setFairId(10L);
        row.setPayerUserId(30L);
        given(paymentMapper.selectById(1L)).willReturn(row);
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).checkAssigned(10L);

        assertThatThrownBy(() -> paymentService.getPayment(1L, 77L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
    }

    @Test
    @DisplayName("존재하지 않는 결제ID를 (paymentId, userId)로 조회하면 예외를 던진다")
    void getPayment_userId오버로드_존재하지않는결제_예외를던진다() {
        given(paymentMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> paymentService.getPayment(999L, 30L))
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

    @Test
    @DisplayName("예약금 결제를 요청하면 예약 도메인의 결제 컨텍스트 금액으로 결제가 생성된다")
    void payReservationDeposit_결제생성_성공() {
        // Arrange: 예약 도메인이 진짜 금액/소유자를 알려주는 상황을 흉내냄
        // (클라이언트가 금액을 안 보내고, 서버가 예약 도메인에 물어봐서 받아온 값을 그대로 씀)
        ReservationPaymentContext context = new ReservationPaymentContext(
                500L, 10L, 90L, "GENERAL", 30000L, LocalDateTime.now().plusMinutes(30)
        );
        given(reservationPaymentContractClient.getPaymentContext(500L)).willReturn(context);

        // Act
        PaymentResponse result = paymentService.payReservationDeposit(500L, 90L);

        // Assert: 응답에 컨텍스트 값(금액 포함)과 기본값(PENDING/TOSS)이 제대로 들어갔는지
        assertThat(result.paymentType()).isEqualTo("RESERVATION_DEPOSIT");
        assertThat(result.amount()).isEqualTo(30000L);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.method()).isEqualTo("TOSS");
        assertThat(result.fairId()).isEqualTo(10L);
        assertThat(result.reservationId()).isEqualTo(500L);
        assertThat(result.payerUserId()).isEqualTo(90L);

        // idempotencyKey가 reservationId 기준으로 만들어졌는지(중복결제 방지의 핵심 값)
        verify(paymentMapper).insert(argThat(row -> "RESERVATION_DEPOSIT_500".equals(row.getIdempotencyKey())));
    }

    @Test
    @DisplayName("예약 소유자가 아닌 사용자가 예약금 결제를 요청하면 예외를 던진다")
    void payReservationDeposit_소유자아님_예외를던진다() {
        // Arrange: 컨텍스트의 payerUserId(90L)와 다른 사용자(999L)가 요청하는 상황(IDOR 방지 확인)
        ReservationPaymentContext context = new ReservationPaymentContext(
                500L, 10L, 90L, "GENERAL", 30000L, LocalDateTime.now().plusMinutes(30)
        );
        given(reservationPaymentContractClient.getPaymentContext(500L)).willReturn(context);

        assertThatThrownBy(() -> paymentService.payReservationDeposit(500L, 999L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        // 소유자 검증에서 걸렸으면 결제 row 자체를 만들면 안 됨
        verify(paymentMapper, never()).insert(any(PaymentRow.class));
    }

    @Test
    @DisplayName("이미 결제된 예약에 다시 예약금 결제를 요청하면 예외를 던진다")
    void payReservationDeposit_중복결제_예외를던진다() {
        // Arrange: idempotencyKey UNIQUE 제약 위반(=이미 결제된 예약)을 흉내냄
        ReservationPaymentContext context = new ReservationPaymentContext(
                500L, 10L, 90L, "GENERAL", 30000L, LocalDateTime.now().plusMinutes(30)
        );
        given(reservationPaymentContractClient.getPaymentContext(500L)).willReturn(context);
        willThrow(new DuplicateKeyException("idempotency key violation"))
                .given(paymentMapper).insert(any(PaymentRow.class));

        assertThatThrownBy(() -> paymentService.payReservationDeposit(500L, 90L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
    }

    @Test
    @DisplayName("행사개설비 결제를 요청하면 결제가 생성된다")
    void payFairOpeningFee_결제생성_성공() {
        // Arrange: 행사 도메인 내부 계약이 승인 시 확정된 금액을 내려주는 상황 흉내
        FairOpeningFeePaymentContext context = new FairOpeningFeePaymentContext(
                10L, 500000L, LocalDateTime.now().plusDays(3)
        );
        given(fairOpeningFeePaymentContractClient.getPaymentContext(10L)).willReturn(context);

        // Act
        PaymentResponse result = paymentService.payFairOpeningFee(10L, 3L);

        // Assert: 참가비·예약금과 동일하게 PENDING/TOSS로 생성되고, fairId만 채워지고
        // businessId·reservationId·applicationId는 전부 null인지
        assertThat(result.paymentType()).isEqualTo("FAIR_OPENING_FEE");
        assertThat(result.amount()).isEqualTo(500000L);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.method()).isEqualTo("TOSS");
        assertThat(result.fairId()).isEqualTo(10L);
        assertThat(result.businessId()).isNull();
        assertThat(result.reservationId()).isNull();
        assertThat(result.applicationId()).isNull();
        assertThat(result.paidAt()).isNull();

        // idempotencyKey가 fairId 기준으로 만들어졌는지(같은 행사 중복결제 방지의 핵심 값)
        verify(paymentMapper).insert(argThat(row -> "FAIR_OPENING_FEE_10".equals(row.getIdempotencyKey())));

        // SUPER_ADMIN role 검증을 거쳤는지(개설비는 신청자 본인이 아니라 SUPER_ADMIN만 결제 가능)
        verify(fairAdminAccessGuard).requireSuperAdmin();
    }

    @Test
    @DisplayName("SUPER_ADMIN이 아니면 행사개설비 결제를 요청해도 예외를 던진다")
    void payFairOpeningFee_SUPER_ADMIN아님_예외를던진다() {
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(fairAdminAccessGuard).requireSuperAdmin();

        assertThatThrownBy(() -> paymentService.payFairOpeningFee(10L, 3L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        // role 검증에서 이미 막혔으니 행사 도메인 계약 조회조차 안 가야 한다
        verify(fairOpeningFeePaymentContractClient, never()).getPaymentContext(any());
    }

    @Test
    @DisplayName("이미 결제된 행사에 다시 개설비 결제를 요청하면 예외를 던진다")
    void payFairOpeningFee_중복결제_예외를던진다() {
        // Arrange: idempotencyKey UNIQUE 제약 위반(=이미 결제된 행사)을 흉내냄
        FairOpeningFeePaymentContext context = new FairOpeningFeePaymentContext(
                10L, 500000L, LocalDateTime.now().plusDays(3)
        );
        given(fairOpeningFeePaymentContractClient.getPaymentContext(10L)).willReturn(context);
        willThrow(new DuplicateKeyException("idempotency key violation"))
                .given(paymentMapper).insert(any(PaymentRow.class));

        assertThatThrownBy(() -> paymentService.payFairOpeningFee(10L, 3L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
    }

    // ── 결제 3종 공통: 실패한 결제(FAILED) 재시도 (2026-08-06 CodeRabbit 지적 해결) ──

    @Test
    @DisplayName("이전 시도가 FAILED로 남은 참가비 결제를 다시 요청하면 그 행을 PENDING으로 재사용한다")
    void payVendorFee_FAILED재시도_기존행을PENDING으로재사용한다() {
        // Arrange: 같은 applicationId로 이전에 시도했다가 실패한 행이 이미 있는 상황
        // (요청자 90L 본인이 결제자였던 행이라 재시도 가능)
        PaymentRow failedRow = new PaymentRow();
        failedRow.setPaymentId(1L);
        failedRow.setStatus("FAILED");
        failedRow.setPayerUserId(90L);
        given(paymentMapper.selectByIdempotencyKey("VENDOR_FEE_40")).willReturn(failedRow);
        given(paymentMapper.resetFailedToPending(eq(1L), eq(60000L), any(), any())).willReturn(1);

        VendorFeePaymentRequest request = new VendorFeePaymentRequest(10L, 20L, 60000L);

        // Act
        PaymentResponse result = paymentService.payVendorFee(40L, 90L, request);

        // Assert: 새 행을 insert하지 않고 기존 FAILED 행을 재사용해서 PENDING으로 응답
        assertThat(result.paymentId()).isEqualTo(1L);
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.amount()).isEqualTo(60000L);
        verify(paymentMapper, never()).insert(any(PaymentRow.class));
    }

    /**
     * 결제 행은 재사용해도 주문번호는 재사용하면 안 된다.
     *
     * <p>실패한 시도의 주문번호에는 토스 쪽에 이미 승인 건이 잡혀 있을 수 있다(사용자는
     * 결제창에서 승인까지 갔는데 우리 confirm이 네트워크 문제로 실패한 경우). 그 값으로
     * 다시 승인을 요청하면 "이미 처리된 주문번호"로 거부당하고, 결제 행을 계속 재사용하는
     * 구조상 그 예약은 재시도할수록 같은 벽에 부딪혀 영영 결제할 수 없게 된다.
     */
    @Test
    @DisplayName("FAILED 재시도는 이전 주문번호를 재사용하지 않고 새로 발급한다")
    void payVendorFee_FAILED재시도_주문번호를새로발급한다() {
        PaymentRow failedRow = new PaymentRow();
        failedRow.setPaymentId(1L);
        failedRow.setStatus("FAILED");
        failedRow.setPayerUserId(90L);
        failedRow.setOrderId("PAYMENT_이전시도에서쓴값");
        given(paymentMapper.selectByIdempotencyKey("VENDOR_FEE_40")).willReturn(failedRow);
        given(paymentMapper.resetFailedToPending(eq(1L), eq(60000L), any(), any())).willReturn(1);

        PaymentResponse result = paymentService.payVendorFee(
                40L, 90L, new VendorFeePaymentRequest(10L, 20L, 60000L));

        // 응답으로 나가는 값(프론트가 결제창에 넘길 값)이 이전 시도와 달라야 한다.
        assertThat(result.orderId()).isNotEqualTo("PAYMENT_이전시도에서쓴값");
        assertThat(result.orderId()).isNotBlank();

        // DB에도 그 새 값이 저장돼야 한다. 응답만 바뀌고 저장이 안 되면 승인 단계에서 어긋난다.
        ArgumentCaptor<String> savedOrderId = ArgumentCaptor.forClass(String.class);
        verify(paymentMapper).resetFailedToPending(
                eq(1L), eq(60000L), savedOrderId.capture(), any());
        assertThat(savedOrderId.getValue()).isEqualTo(result.orderId());
    }

    /**
     * 프론트가 결제창에 넘긴 주문번호와 서버가 승인에 쓰는 주문번호는 반드시 같아야 한다.
     * 예전처럼 승인 시점에 다시 만들면 시도마다 값이 갈려 승인이 통째로 실패한다.
     */
    @Test
    @DisplayName("승인은 저장된 주문번호를 그대로 토스에 보낸다")
    void confirmPayment_저장된주문번호를사용한다() {
        PaymentRow pending = new PaymentRow();
        pending.setPaymentId(7L);
        pending.setStatus("PENDING");
        pending.setPayerUserId(90L);
        pending.setAmount(60000L);
        pending.setPaymentType("VENDOR_FEE");
        pending.setApplicationId(40L);
        pending.setOrderId("PAYMENT_저장된주문번호");
        given(paymentMapper.selectById(7L)).willReturn(pending);
        given(paymentMapper.markProcessing(eq(7L), any())).willReturn(1);
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(any(), any(), any()))
                .willReturn(new TossPaymentResponse("toss-key", "PAYMENT_저장된주문번호", "DONE", 60000L, "CARD", null, null, null));

        paymentService.confirmPayment(7L, 90L, new ConfirmPaymentRequest("toss-key"));

        verify(tossPaymentClient).confirmPayment("toss-key", "PAYMENT_저장된주문번호", 60000L);
    }

    @Test
    @DisplayName("FAILED 재시도 중 다른 요청이 먼저 선점하면(재시도 경쟁) 예외를 던진다")
    void payVendorFee_FAILED재시도경쟁_선점실패시예외를던진다() {
        // Arrange: resetFailedToPending의 WHERE status='FAILED' 가드에서 밀린 상황을 흉내냄
        PaymentRow failedRow = new PaymentRow();
        failedRow.setPaymentId(1L);
        failedRow.setStatus("FAILED");
        failedRow.setPayerUserId(90L);
        given(paymentMapper.selectByIdempotencyKey("VENDOR_FEE_40")).willReturn(failedRow);
        given(paymentMapper.resetFailedToPending(eq(1L), any(), any(), any())).willReturn(0);

        VendorFeePaymentRequest request = new VendorFeePaymentRequest(10L, 20L, 60000L);

        assertThatThrownBy(() -> paymentService.payVendorFee(40L, 90L, request))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
    }

    @Test
    @DisplayName("다른 사용자가 남의 FAILED 참가비 결제를 재시도하면 예외를 던진다")
    void payVendorFee_FAILED재시도_결제자아님_예외를던진다() {
        // Arrange: 원래 결제자는 90L인데, 다른 사용자(999L)가 같은 applicationId로 재시도하는 상황
        // (CodeRabbit 지적, PR #63 — 남의 FAILED 결제를 PENDING으로 되돌려 잠가버릴 수 있던 문제)
        PaymentRow failedRow = new PaymentRow();
        failedRow.setPaymentId(1L);
        failedRow.setStatus("FAILED");
        failedRow.setPayerUserId(90L);
        given(paymentMapper.selectByIdempotencyKey("VENDOR_FEE_40")).willReturn(failedRow);

        VendorFeePaymentRequest request = new VendorFeePaymentRequest(10L, 20L, 60000L);

        assertThatThrownBy(() -> paymentService.payVendorFee(40L, 999L, request))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        // 결제자가 아니면 재시도 자체(선점 시도)를 하면 안 됨
        verify(paymentMapper, never()).resetFailedToPending(any(), any(), any(), any());
    }

    @Test
    @DisplayName("다른 사용자가 남의 PENDING 참가비 결제를 재개하려 하면 예외를 던진다")
    void payVendorFee_PENDING재개_결제자아님_예외를던진다() {
        // 원래 결제자는 90L인데 다른 사용자(999L)가 같은 applicationId로 "결제 계속하기"를 누른 상황.
        // 예약금(payReservationDeposit)은 앞단에 소유자 검증이 따로 있지만, 참가비에는 없어서
        // createOrRetryPayment의 PENDING 분기가 유일한 방어선이다 — 남의 진행 중 결제(같은 orderId)를
        // 넘겨받아 결제창을 열지 못하게 막아야 한다.
        PaymentRow pendingRow = new PaymentRow();
        pendingRow.setPaymentId(1L);
        pendingRow.setStatus("PENDING");
        pendingRow.setPayerUserId(90L);
        pendingRow.setOrderId("PAYMENT_original");
        given(paymentMapper.selectByIdempotencyKey("VENDOR_FEE_40")).willReturn(pendingRow);

        VendorFeePaymentRequest request = new VendorFeePaymentRequest(10L, 20L, 60000L);

        assertThatThrownBy(() -> paymentService.payVendorFee(40L, 999L, request))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        // 남의 결제이므로 새 결제 insert도, FAILED 재사용(resetFailedToPending)도 하면 안 된다.
        verify(paymentMapper, never()).insert(any(PaymentRow.class));
        verify(paymentMapper, never()).resetFailedToPending(any(), any(), any(), any());
    }

    @Test
    @DisplayName("이미 PROCESSING/COMPLETED인 참가비 결제가 있으면 재사용 불가라서 여전히 중복결제로 막는다")
    void payVendorFee_재사용불가상태기존결제있으면_예외를던진다() {
        // PROCESSING(승인 진행 중)은 PENDING과 달리 재개 대상이 아니다 — 계속 막아야 한다.
        PaymentRow processingRow = new PaymentRow();
        processingRow.setPaymentId(1L);
        processingRow.setStatus("PROCESSING");
        given(paymentMapper.selectByIdempotencyKey("VENDOR_FEE_40")).willReturn(processingRow);

        VendorFeePaymentRequest request = new VendorFeePaymentRequest(10L, 20L, 60000L);

        assertThatThrownBy(() -> paymentService.payVendorFee(40L, 90L, request))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
        verify(paymentMapper, never()).resetFailedToPending(any(), any(), any(), any());
        verify(paymentMapper, never()).insert(any(PaymentRow.class));
    }

    @Test
    @DisplayName("이전 시도가 FAILED로 남은 예약금 결제를 다시 요청하면 그 행을 PENDING으로 재사용한다")
    void payReservationDeposit_FAILED재시도_기존행을PENDING으로재사용한다() {
        ReservationPaymentContext context = new ReservationPaymentContext(
                500L, 10L, 90L, "GENERAL", 30000L, LocalDateTime.now().plusMinutes(30)
        );
        given(reservationPaymentContractClient.getPaymentContext(500L)).willReturn(context);

        PaymentRow failedRow = new PaymentRow();
        failedRow.setPaymentId(2L);
        failedRow.setStatus("FAILED");
        failedRow.setPayerUserId(90L);
        given(paymentMapper.selectByIdempotencyKey("RESERVATION_DEPOSIT_500")).willReturn(failedRow);
        given(paymentMapper.resetFailedToPending(eq(2L), eq(30000L), any(), any())).willReturn(1);

        PaymentResponse result = paymentService.payReservationDeposit(500L, 90L);

        assertThat(result.paymentId()).isEqualTo(2L);
        assertThat(result.status()).isEqualTo("PENDING");
        verify(paymentMapper, never()).insert(any(PaymentRow.class));
    }

    @Test
    @DisplayName("결제창을 닫아 PENDING으로 남은 예약금 결제를 같은 사용자가 다시 요청하면 orderId를 바꾸지 않고 그대로 돌려준다")
    void payReservationDeposit_PENDING동일사용자_같은orderId로그대로재사용한다() {
        // 사용자가 토스 결제창을 그냥 닫으면 백엔드는 통보를 못 받아 결제가 PENDING으로 남는다.
        // 다시 "결제 계속하기"를 누르면 새 결제도, 새 orderId도 만들지 않고 그 PENDING을 '그대로' 돌려줘야 한다.
        // (혹시 이미 승인됐는데 통보만 놓친 경우, 새 orderId면 이중결제가 나므로 같은 orderId 유지가 핵심.)
        ReservationPaymentContext context = new ReservationPaymentContext(
                500L, 10L, 90L, "GENERAL", 30000L, LocalDateTime.now().plusMinutes(30)
        );
        given(reservationPaymentContractClient.getPaymentContext(500L)).willReturn(context);

        PaymentRow pendingRow = new PaymentRow();
        pendingRow.setPaymentId(7L);
        pendingRow.setStatus("PENDING");
        pendingRow.setPayerUserId(90L);
        pendingRow.setOrderId("PAYMENT_original");
        given(paymentMapper.selectByIdempotencyKey("RESERVATION_DEPOSIT_500")).willReturn(pendingRow);

        PaymentResponse result = paymentService.payReservationDeposit(500L, 90L);

        assertThat(result.paymentId()).isEqualTo(7L);
        assertThat(result.status()).isEqualTo("PENDING");
        // ★ orderId를 새로 발급하지 않고 기존 값을 그대로 유지해야 한다(이중결제 방지 핵심).
        assertThat(result.orderId()).isEqualTo("PAYMENT_original");
        // 기존 PENDING을 그대로 돌려줄 뿐 — 새 결제 insert도, FAILED 재사용(resetFailedToPending)도 하지 않는다.
        verify(paymentMapper, never()).insert(any(PaymentRow.class));
        verify(paymentMapper, never()).resetFailedToPending(any(), any(), any(), any());
    }

    @Test
    @DisplayName("이전 시도가 FAILED로 남은 개설비 결제를 다시 요청하면 그 행을 PENDING으로 재사용한다")
    void payFairOpeningFee_FAILED재시도_기존행을PENDING으로재사용한다() {
        FairOpeningFeePaymentContext context = new FairOpeningFeePaymentContext(
                10L, 500000L, LocalDateTime.now().plusDays(3)
        );
        given(fairOpeningFeePaymentContractClient.getPaymentContext(10L)).willReturn(context);

        PaymentRow failedRow = new PaymentRow();
        failedRow.setPaymentId(3L);
        failedRow.setStatus("FAILED");
        failedRow.setPayerUserId(3L);
        given(paymentMapper.selectByIdempotencyKey("FAIR_OPENING_FEE_10")).willReturn(failedRow);
        given(paymentMapper.resetFailedToPending(eq(3L), eq(500000L), any(), any())).willReturn(1);

        PaymentResponse result = paymentService.payFairOpeningFee(10L, 3L);

        assertThat(result.paymentId()).isEqualTo(3L);
        assertThat(result.status()).isEqualTo("PENDING");
        verify(paymentMapper, never()).insert(any(PaymentRow.class));
    }

    @Test
    @DisplayName("다른 사용자가 남의 FAILED 개설비 결제를 재시도하면 예외를 던진다")
    void payFairOpeningFee_FAILED재시도_결제자아님_예외를던진다() {
        FairOpeningFeePaymentContext context = new FairOpeningFeePaymentContext(
                10L, 500000L, LocalDateTime.now().plusDays(3)
        );
        given(fairOpeningFeePaymentContractClient.getPaymentContext(10L)).willReturn(context);

        PaymentRow failedRow = new PaymentRow();
        failedRow.setPaymentId(3L);
        failedRow.setStatus("FAILED");
        failedRow.setPayerUserId(3L);
        given(paymentMapper.selectByIdempotencyKey("FAIR_OPENING_FEE_10")).willReturn(failedRow);

        assertThatThrownBy(() -> paymentService.payFairOpeningFee(10L, 999L))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);
        verify(paymentMapper, never()).resetFailedToPending(any(), any(), any(), any());
    }

    // PENDING 상태의 결제 하나를 미리 만들어두는 헬퍼. confirmPayment 테스트들이
    // 전부 "PENDING인 결제가 이미 있다"는 상황에서 시작하므로 중복을 줄이려고 뺐음.
    private PaymentRow pendingRow() {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(1L);
        // 주문번호는 이제 DB에 저장된 값을 쓴다. 발급 규칙이 아니라 "저장된 값이 그대로 나간다"가
        // 검증 대상이라, 픽스처에서는 읽기 쉬운 값을 넣는다.
        row.setOrderId("PAYMENT_1");
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
        given(paymentMapper.markProcessing(eq(1L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_1"), eq(50000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_1", "DONE", 50000L, "카드", OffsetDateTime.now(), null, null
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
    @DisplayName("간편결제로 결제하면 제공사가 저장된다")
    void confirmPayment_간편결제_제공사가저장된다() {
        PaymentRow row = pendingRow();
        given(paymentMapper.selectById(1L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(1L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_1"), eq(50000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_1", "DONE", 50000L, "카드",
                        OffsetDateTime.now(), new TossPaymentResponse.EasyPay("네이버페이"), null
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);

        PaymentResponse result = paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123"));

        assertThat(result.easyPayProvider()).isEqualTo("네이버페이");
        // 저장 자체(마퍼 호출 인자)까지 확인 — 응답 DTO만 맞고 실제 UPDATE에는 안 실리는
        // 버그(과거 markCompleted SQL에 컬럼 누락)를 이 검증이 잡는다.
        verify(paymentMapper).markCompleted(argThat(saved -> "네이버페이".equals(saved.getEasyPayProvider())));
    }

    @Test
    @DisplayName("일반 결제(카드 등)는 간편결제 제공사가 null로 저장된다")
    void confirmPayment_일반결제_제공사는null() {
        PaymentRow row = pendingRow();
        given(paymentMapper.selectById(1L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(1L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_1"), eq(50000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_1", "DONE", 50000L, "카드", OffsetDateTime.now(), null, null
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);

        PaymentResponse result = paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123"));

        assertThat(result.easyPayProvider()).isNull();
    }

    @Test
    @DisplayName("가상계좌로 결제하면 즉시 COMPLETED로 확정하지 않고 WAITING_FOR_DEPOSIT으로 남긴다")
    void confirmPayment_가상계좌_입금대기로남는다() {
        PaymentRow row = pendingRow();
        given(paymentMapper.selectById(1L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(1L), any(LocalDateTime.class))).willReturn(1);
        LocalDateTime dueDate = LocalDateTime.of(2026, 9, 1, 23, 59);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_1"), eq(50000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_1", "WAITING_FOR_DEPOSIT", 50000L, "가상계좌",
                        OffsetDateTime.now(), null,
                        new TossPaymentResponse.VirtualAccount("020", "1234567890", dueDate, "secret-abc")
                ));
        given(paymentMapper.markWaitingForDeposit(any(PaymentRow.class))).willReturn(1);

        PaymentResponse result = paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123"));

        assertThat(result.status()).isEqualTo("WAITING_FOR_DEPOSIT");
        assertThat(result.virtualAccountBankCode()).isEqualTo("020");
        assertThat(result.virtualAccountNumber()).isEqualTo("1234567890");
        assertThat(result.virtualAccountDueDate()).isEqualTo(dueDate);
        // secret이 저장되지 않으면 이후 입금 웹훅이 전부 무시된다 — 저장 인자까지 검증한다.
        verify(paymentMapper).markWaitingForDeposit(argThat(
                saved -> "secret-abc".equals(saved.getVirtualAccountSecret())
                        && "020".equals(saved.getVirtualAccountBankCode())));
        // 입금 전이니 결제완료 처리(markCompleted)도, 참가비 확정 통지도 아직 일어나면 안 된다
        verify(paymentMapper, never()).markCompleted(any(PaymentRow.class));
        verify(applicationService, never()).confirmVendorPayment(anyLong(), anyLong(), anyLong());
    }

    @Test
    @DisplayName("예약금 결제에 가상계좌가 시도되면 승인을 거절하고 FAILED로 남긴다 - 입금 대기로 저장하지 않는다")
    void confirmPayment_예약금가상계좌_거절한다() {
        // 예약 결제 제한시간(10분)과 가상계좌 입금기한(하루 단위)이 애초에 맞지 않아 예약금 결제
        // 화면에서는 가상계좌 선택지를 없앴다(프론트 타입으로도 막았음). 여기서 막는 건 그 화면을
        // 우회한 요청(브라우저에 남은 옛 번들·SDK 직접 호출)이다.
        PaymentRow row = pendingReservationDepositRow();
        given(paymentMapper.selectById(2L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(2L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_2"), eq(30000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_2", "WAITING_FOR_DEPOSIT", 30000L, "가상계좌",
                        OffsetDateTime.now(), null,
                        new TossPaymentResponse.VirtualAccount(
                                "020", "1234567890", LocalDateTime.of(2026, 9, 1, 23, 59), "secret-abc")
                ));

        assertThatThrownBy(() -> paymentService.confirmPayment(2L, 90L, new ConfirmPaymentRequest("paymentKey123")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_METHOD_NOT_ALLOWED);

        // 재시도할 수 있게 FAILED로 남기고, 계좌정보·secret은 저장하지 않아야 한다 - 저장하면
        // 이후 입금 웹훅이 secret 대조를 통과해 "예약 없는 결제"가 완료로 바뀐다.
        verify(paymentMapper).markFailed(eq(2L), any(LocalDateTime.class));
        verify(paymentMapper, never()).markWaitingForDeposit(any(PaymentRow.class));
        verify(paymentMapper, never()).markCompleted(any(PaymentRow.class));
        verify(reservationPaymentContractClient, never())
                .completePayment(any(), any(), any(), any(), any());
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
    @DisplayName("동시에 두 번 승인 요청이 들어오면 선점에 실패한 쪽은 토스를 부르지도 않고 예외를 던진다")
    void confirmPayment_동시승인_선점실패시토스호출안함() {
        // Arrange: markProcessing이 0을 반환 = 다른 요청이 먼저 PENDING -> PROCESSING을 선점한 상황
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        given(paymentMapper.markProcessing(eq(1L), any(LocalDateTime.class))).willReturn(0);

        assertThatThrownBy(() -> paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);

        // 핵심: 선점에 실패했으면 토스 승인 API 자체를 호출하면 안 됨(중복 승인 시도 방지)
        verify(tossPaymentClient, never()).confirmPayment(any(), any(), any());
    }

    // ── 결제 취소·만료 (WBS 1.7) — 다른 도메인의 만료/취소 배치가 호출하는 상태전이 ──
    // pendingRow()는 VENDOR_FEE라 캐스터는 "VENDOR_APPLICATION",
    // pendingReservationDepositRow()는 RESERVATION_DEPOSIT이라 캐스터는 "RESERVATION".

    @Test
    @DisplayName("PENDING 결제를 취소하면 CANCELED로 바뀐다")
    void cancelPayment_PENDING상태면_CANCELED로바뀐다() {
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        given(paymentMapper.markCanceled(eq(1L), any(LocalDateTime.class))).willReturn(1);

        PaymentResponse result = paymentService.cancelPayment(1L, "VENDOR_APPLICATION");

        assertThat(result.status()).isEqualTo("CANCELED");
        verify(paymentMapper).markCanceled(eq(1L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("WAITING_FOR_DEPOSIT 결제도 취소할 수 있다 — 아직 실제 입금 전이라 PENDING과 동일하게 안전함")
    void cancelPayment_WAITING_FOR_DEPOSIT상태도_CANCELED로바뀐다() {
        // 가상계좌 발급까지는 됐지만 아직 입금 전인 결제를, 그 원업무(예약 등)가 취소되면서
        // 함께 정리하려는 상황(2026-08-18 CodeRabbit 리뷰 지적 — 이 상태를 못 다뤄서
        // 원업무가 취소돼도 이 결제만 계속 대기 상태로 남는 사각지대였음).
        PaymentRow row = pendingRow();
        row.setStatus("WAITING_FOR_DEPOSIT");
        given(paymentMapper.selectById(1L)).willReturn(row);
        given(paymentMapper.markCanceled(eq(1L), any(LocalDateTime.class))).willReturn(1);

        PaymentResponse result = paymentService.cancelPayment(1L, "VENDOR_APPLICATION");

        assertThat(result.status()).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("존재하지 않는 결제를 취소하려 하면 예외를 던진다")
    void cancelPayment_결제없음_예외를던진다() {
        given(paymentMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> paymentService.cancelPayment(999L, "VENDOR_APPLICATION"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("PENDING이 아닌 결제를 취소하려 하면 예외를 던지고 마킹을 시도조차 안 한다")
    void cancelPayment_PENDING아니면_예외를던진다() {
        // COMPLETED된 결제를 뒤늦게 취소하려는 상황(원 업무 배치가 타이밍을 놓친 경우 등)
        PaymentRow row = pendingRow();
        row.setStatus("COMPLETED");
        given(paymentMapper.selectById(1L)).willReturn(row);

        assertThatThrownBy(() -> paymentService.cancelPayment(1L, "VENDOR_APPLICATION"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);

        // PROCESSING 중인 결제를 배치가 잘못 건드리면 안 되므로, 애초에 마킹 호출 자체가 없어야 함
        verify(paymentMapper, never()).markCanceled(any(), any());
    }

    @Test
    @DisplayName("취소 요청이 동시에 들어와 선점에 실패하면(이미 confirm이 앞서 나감) 예외를 던진다")
    void cancelPayment_선점실패시_예외를던진다() {
        // markCanceled의 WHERE status='PENDING' 가드에서 밀린 상황 —
        // 예: 배치가 조회한 직후 사용자가 먼저 confirm(PENDING->PROCESSING)해버린 경쟁
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        given(paymentMapper.markCanceled(eq(1L), any(LocalDateTime.class))).willReturn(0);

        assertThatThrownBy(() -> paymentService.cancelPayment(1L, "VENDOR_APPLICATION"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
    }

    @Test
    @DisplayName("호출 도메인이 그 결제의 소유 도메인이 아니면 취소를 거부한다")
    void cancelPayment_캐스터불일치_예외를던진다() {
        // VENDOR_FEE 결제인데 RESERVATION 도메인이 취소하려는 상황 — 캐스터 값 자체는
        // 허용목록에 있어도 그 결제의 소유 도메인인지는 확인해야 한다.
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());

        assertThatThrownBy(() -> paymentService.cancelPayment(1L, "RESERVATION"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(paymentMapper, never()).markCanceled(any(), any());
    }

    @Test
    @DisplayName("FAIR 도메인은 예외적으로 예약금·참가비·개설비 결제를 전부 취소할 수 있다")
    void cancelPayment_FAIR도메인은_세유형다_취소할수있다() {
        // 행사가 취소되면 그 행사에 딸린 예약금(RESERVATION_DEPOSIT)/참가비(VENDOR_FEE) PENDING
        // 결제까지 Fair 도메인이 한 번에 정리한다 - reservation/vendor application 도메인이
        // 각자 fairs.canceled_at을 감지해서 반응하는 로직을 따로 만들지 않아도 되게 하려는 목적.
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        given(paymentMapper.markCanceled(eq(1L), any(LocalDateTime.class))).willReturn(1);
        assertThat(paymentService.cancelPayment(1L, "FAIR").status()).isEqualTo("CANCELED");

        given(paymentMapper.selectById(2L)).willReturn(pendingReservationDepositRow());
        given(paymentMapper.markCanceled(eq(2L), any(LocalDateTime.class))).willReturn(1);
        assertThat(paymentService.cancelPayment(2L, "FAIR").status()).isEqualTo("CANCELED");

        given(paymentMapper.selectById(3L)).willReturn(pendingOpeningFeeRow());
        given(paymentMapper.markCanceled(eq(3L), any(LocalDateTime.class))).willReturn(1);
        assertThat(paymentService.cancelPayment(3L, "FAIR").status()).isEqualTo("CANCELED");
    }

    @Test
    @DisplayName("CALLER_PAYMENT_TYPES에 등록 안 된 호출 도메인은 어떤 결제유형이든 거부한다")
    void cancelPayment_미등록캐스터는_예외를던진다() {
        // 오타나 아직 등록 안 된 호출 도메인이 오면 Map.get()이 null을 반환하는데, 이걸
        // "제한 없음"으로 잘못 취급하면 미등록 호출자가 아무 결제나 건드릴 수 있게 열려버린다.
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());

        assertThatThrownBy(() -> paymentService.cancelPayment(1L, "UNKNOWN_DOMAIN"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(paymentMapper, never()).markCanceled(any(), any());
    }

    @Test
    @DisplayName("PENDING 결제를 만료 처리하면 EXPIRED로 바뀐다")
    void expirePayment_PENDING상태면_EXPIRED로바뀐다() {
        given(paymentMapper.selectById(2L)).willReturn(pendingReservationDepositRow());
        given(paymentMapper.markExpired(eq(2L), any(LocalDateTime.class))).willReturn(1);

        PaymentResponse result = paymentService.expirePayment(2L, "RESERVATION");

        assertThat(result.status()).isEqualTo("EXPIRED");
        verify(paymentMapper).markExpired(eq(2L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("WAITING_FOR_DEPOSIT 결제도 만료 처리할 수 있다")
    void expirePayment_WAITING_FOR_DEPOSIT상태도_EXPIRED로바뀐다() {
        PaymentRow row = pendingReservationDepositRow();
        row.setStatus("WAITING_FOR_DEPOSIT");
        given(paymentMapper.selectById(2L)).willReturn(row);
        given(paymentMapper.markExpired(eq(2L), any(LocalDateTime.class))).willReturn(1);

        PaymentResponse result = paymentService.expirePayment(2L, "RESERVATION");

        assertThat(result.status()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("존재하지 않는 결제를 만료 처리하려 하면 예외를 던진다")
    void expirePayment_결제없음_예외를던진다() {
        given(paymentMapper.selectById(999L)).willReturn(null);

        assertThatThrownBy(() -> paymentService.expirePayment(999L, "RESERVATION"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_NOT_FOUND);
    }

    @Test
    @DisplayName("PENDING이 아닌 결제를 만료 처리하려 하면 예외를 던지고 마킹을 시도조차 안 한다")
    void expirePayment_PENDING아니면_예외를던진다() {
        PaymentRow row = pendingReservationDepositRow();
        row.setStatus("COMPLETED");
        given(paymentMapper.selectById(2L)).willReturn(row);

        assertThatThrownBy(() -> paymentService.expirePayment(2L, "RESERVATION"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);

        verify(paymentMapper, never()).markExpired(any(), any());
    }

    @Test
    @DisplayName("만료 요청이 동시에 들어와 선점에 실패하면 예외를 던진다")
    void expirePayment_선점실패시_예외를던진다() {
        given(paymentMapper.selectById(2L)).willReturn(pendingReservationDepositRow());
        given(paymentMapper.markExpired(eq(2L), any(LocalDateTime.class))).willReturn(0);

        assertThatThrownBy(() -> paymentService.expirePayment(2L, "RESERVATION"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE);
    }

    @Test
    @DisplayName("호출 도메인이 그 결제의 소유 도메인이 아니면 만료 처리를 거부한다")
    void expirePayment_캐스터불일치_예외를던진다() {
        // RESERVATION_DEPOSIT 결제인데 VENDOR_APPLICATION 도메인이 만료 처리하려는 상황
        given(paymentMapper.selectById(2L)).willReturn(pendingReservationDepositRow());

        assertThatThrownBy(() -> paymentService.expirePayment(2L, "VENDOR_APPLICATION"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(paymentMapper, never()).markExpired(any(), any());
    }

    @Test
    @DisplayName("FAIR 도메인은 예외적으로 예약금·참가비·개설비 결제를 전부 만료 처리할 수 있다")
    void expirePayment_FAIR도메인은_세유형다_만료처리할수있다() {
        given(paymentMapper.selectById(2L)).willReturn(pendingReservationDepositRow());
        given(paymentMapper.markExpired(eq(2L), any(LocalDateTime.class))).willReturn(1);
        assertThat(paymentService.expirePayment(2L, "FAIR").status()).isEqualTo("EXPIRED");

        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        given(paymentMapper.markExpired(eq(1L), any(LocalDateTime.class))).willReturn(1);
        assertThat(paymentService.expirePayment(1L, "FAIR").status()).isEqualTo("EXPIRED");

        given(paymentMapper.selectById(3L)).willReturn(pendingOpeningFeeRow());
        given(paymentMapper.markExpired(eq(3L), any(LocalDateTime.class))).willReturn(1);
        assertThat(paymentService.expirePayment(3L, "FAIR").status()).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("CALLER_PAYMENT_TYPES에 등록 안 된 호출 도메인은 어떤 결제유형이든 만료 처리를 거부한다")
    void expirePayment_미등록캐스터는_예외를던진다() {
        given(paymentMapper.selectById(2L)).willReturn(pendingReservationDepositRow());

        assertThatThrownBy(() -> paymentService.expirePayment(2L, "UNKNOWN_DOMAIN"))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.ACCESS_DENIED);

        verify(paymentMapper, never()).markExpired(any(), any());
    }

    @Test
    @DisplayName("토스가 승인을 확정적으로 거부하면(4xx) 결제를 FAILED로 남긴다")
    void confirmPayment_토스승인거부_FAILED로전이한다() {
        // Arrange: 토스 클라이언트가 4xx를 이미 PAYMENT_APPROVAL_FAILED로 변환해서 던지는 상황을 흉내냄
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        given(paymentMapper.markProcessing(eq(1L), any(LocalDateTime.class))).willReturn(1);
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
        // Arrange: 5xx는 실제로 승인됐을 수도 있어서 실패로 단정하면 안 됨(PROCESSING 유지)
        given(paymentMapper.selectById(1L)).willReturn(pendingRow());
        given(paymentMapper.markProcessing(eq(1L), any(LocalDateTime.class))).willReturn(1);
        willThrow(new CommonException(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE))
                .given(tossPaymentClient).confirmPayment(any(), any(), any());

        assertThatThrownBy(() -> paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123")))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.PAYMENT_GATEWAY_UNAVAILABLE);

        // markFailed/markCompleted 둘 다 호출되면 안 됨 — 상태는 선점된 PROCESSING 그대로 유지
        verify(paymentMapper, never()).markFailed(any(), any());
        verify(paymentMapper, never()).markCompleted(any());
    }

    // PENDING 상태의 예약금 결제 하나를 미리 만들어두는 헬퍼. pendingRow()와 거의 같지만
    // paymentType/reservationId가 예약금 결제 케이스에 맞춰져 있음.
    private PaymentRow pendingReservationDepositRow() {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(2L);
        // 주문번호는 이제 DB에 저장된 값을 쓴다. 발급 규칙이 아니라 "저장된 값이 그대로 나간다"가
        // 검증 대상이라, 픽스처에서는 읽기 쉬운 값을 넣는다.
        row.setOrderId("PAYMENT_2");
        row.setPaymentType("RESERVATION_DEPOSIT");
        row.setAmount(30000L);
        row.setStatus("PENDING");
        row.setMethod("TOSS");
        row.setPayerUserId(90L);
        row.setFairId(10L);
        row.setReservationId(500L);
        return row;
    }

    // PENDING 상태의 개설비 결제 하나를 미리 만들어두는 헬퍼. FAIR 캐스터가 세 유형을
    // 전부 다룰 수 있는지 검증할 때 pendingRow()/pendingReservationDepositRow()와 함께 쓴다.
    private PaymentRow pendingOpeningFeeRow() {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(3L);
        // 주문번호는 이제 DB에 저장된 값을 쓴다. 발급 규칙이 아니라 "저장된 값이 그대로 나간다"가
        // 검증 대상이라, 픽스처에서는 읽기 쉬운 값을 넣는다.
        row.setOrderId("PAYMENT_3");
        row.setPaymentType("FAIR_OPENING_FEE");
        row.setAmount(100000L);
        row.setStatus("PENDING");
        row.setMethod("TOSS");
        row.setPayerUserId(90L);
        row.setFairId(10L);
        return row;
    }

    @Test
    @DisplayName("예약금 결제 승인이 완료되면 예약 도메인에 결제완료를 통지한다")
    void confirmPayment_예약금결제완료시_예약도메인에통지한다() {
        // Arrange
        PaymentRow row = pendingReservationDepositRow();
        given(paymentMapper.selectById(2L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(2L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_2"), eq(30000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_2", "DONE", 30000L, "카드", OffsetDateTime.now(), null, null
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);

        // Act
        PaymentResponse result = paymentService.confirmPayment(2L, 90L, new ConfirmPaymentRequest("paymentKey123"));

        // Assert: 결제 자체는 정상 완료되고
        assertThat(result.status()).isEqualTo("COMPLETED");
        // 예약 도메인에 completePayment로 통지까지 갔는지 확인
        // (eventId는 "PAYMENT_" + paymentId 규칙 — 예약 쪽 멱등 처리 키로 쓰임)
        verify(reservationPaymentContractClient).completePayment(
                eq("PAYMENT_2"), eq(2L), eq(500L), eq(30000L), any(LocalDateTime.class)
        );
    }

    @Test
    @DisplayName("참가비 결제가 완료되면 예약 도메인에는 통지하지 않는다")
    void confirmPayment_참가비결제완료시_예약도메인통지안함() {
        // Arrange: VENDOR_FEE 결제라 예약 도메인과 아무 관련 없는 상황
        PaymentRow row = pendingRow();
        given(paymentMapper.selectById(1L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(1L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_1"), eq(50000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_1", "DONE", 50000L, "카드", OffsetDateTime.now(), null, null
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);

        // Act
        paymentService.confirmPayment(1L, 90L, new ConfirmPaymentRequest("paymentKey123"));

        // Assert: paymentType 분기가 제대로 걸러서 예약 도메인은 아예 안 부르는지
        verify(reservationPaymentContractClient, never()).completePayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("예약 도메인 통지가 실패해도 결제 응답 자체는 성공으로 반환한다")
    void confirmPayment_예약도메인통지실패해도_결제응답은성공이다() {
        // Arrange
        PaymentRow row = pendingReservationDepositRow();
        given(paymentMapper.selectById(2L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(2L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_2"), eq(30000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_2", "DONE", 30000L, "카드", OffsetDateTime.now(), null, null
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);
        // 예약 도메인 통지 자체가 터지는 상황(네트워크 장애 등)을 흉내냄
        willThrow(new RuntimeException("connection refused"))
                .given(reservationPaymentContractClient).completePayment(any(), any(), any(), any(), any());

        // Act & Assert: 통지가 실패해도 예외가 밖으로 안 새고 결제는 COMPLETED로 정상 반환돼야 함
        // ("결제는 됐는데 예약 확정 통지만 실패"는 로그만 남기고 응답은 성공 처리하기로 한 설계상 결정)
        PaymentResponse result = paymentService.confirmPayment(2L, 90L, new ConfirmPaymentRequest("paymentKey123"));
        assertThat(result.status()).isEqualTo("COMPLETED");

        // 계속 실패해도 포기하기 전까지 최대 재시도 횟수(3번)만큼은 시도했는지
        verify(reservationPaymentContractClient, times(3)).completePayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("예약 도메인 통지가 첫 시도에만 실패해도 재시도해서 결국 성공한다")
    void confirmPayment_통지일시적실패_재시도로성공한다() {
        // Arrange
        PaymentRow row = pendingReservationDepositRow();
        given(paymentMapper.selectById(2L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(2L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_2"), eq(30000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_2", "DONE", 30000L, "카드", OffsetDateTime.now(), null, null
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);
        // 첫 번째 시도만 실패하고 두 번째 시도부터는 성공하는 상황(일시적 네트워크 장애를 흉내냄).
        // completePayment는 void가 아니라 값을 리턴하는 메서드라 willDoNothing()은 못 쓰고,
        // 두 번째 호출부터는 willReturn으로 정상 응답을 흉내낸다(서비스가 리턴값을 안 쓰긴 하지만).
        // eventId가 매 시도 동일해서 예약 도메인이 멱등 처리해준다는 전제가 있어야 안전한 재시도임.
        given(reservationPaymentContractClient.completePayment(any(), any(), any(), any(), any()))
                .willThrow(new RuntimeException("timeout"))
                .willReturn(new ReservationPaymentCompletionResult(500L, "CONFIRMED", false, "qr-token"));

        // Act
        PaymentResponse result = paymentService.confirmPayment(2L, 90L, new ConfirmPaymentRequest("paymentKey123"));

        // Assert: 결제는 정상 완료되고, 통지는 재시도 끝에 두 번째 시도에서 성공해서 멈췄는지
        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(reservationPaymentContractClient, times(2)).completePayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("예약 도메인이 4xx로 결제완료 통지를 거부하면(제한시간 초과 등) 재시도 없이 바로 자동 환불한다")
    void confirmPayment_예약도메인이4xx로거부_재시도없이자동환불한다() {
        // Arrange: 가상계좌 입금이 늦어져 예약이 이미 만료된 뒤 뒤늦게 입금완료 통지가 가는 상황
        // (이슈 #167 — 예약 10분 제한시간과 가상계좌 입금기한 불일치로 실제 발생 가능한 케이스).
        // ReservationPaymentCompletionService가 RESERVATION_PAYMENT_EXPIRED(409)로 거부한다.
        PaymentRow row = pendingReservationDepositRow();
        given(paymentMapper.selectById(2L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(2L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_2"), eq(30000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_2", "DONE", 30000L, "카드", OffsetDateTime.now(), null, null
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);
        willThrow(HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, new byte[0], null))
                .given(reservationPaymentContractClient).completePayment(any(), any(), any(), any(), any());

        // Act: 결제 자체는 이미 성공했으므로 예외 없이 정상 응답(COMPLETED)이 나와야 한다
        PaymentResponse result = paymentService.confirmPayment(2L, 90L, new ConfirmPaymentRequest("paymentKey123"));

        // Assert
        assertThat(result.status()).isEqualTo("COMPLETED");
        // 재시도 없이 딱 1번만 시도하고 바로 포기해야 한다 — 4xx는 재시도해도 결과가 똑같다.
        verify(reservationPaymentContractClient, times(1)).completePayment(any(), any(), any(), any(), any());
        // "예약도 없고 환불도 안 되는" 상태를 막기 위한 자동 환불이 걸렸는지
        verify(refundService).refund(eq(2L), eq(0L), argThat(
                req -> req.refundReason() == RefundReason.USER_CANCEL
                        && req.requestedByDomain() == RequestedByDomain.PAYMENT_ADMIN));
    }

    @Test
    @DisplayName("예약 도메인 5xx(서버 장애)는 4xx와 달리 자동 환불하지 않고 기존처럼 재시도만 한다")
    void confirmPayment_예약도메인5xx는_자동환불하지않고재시도한다() {
        // 5xx는 예약 도메인 쪽 일시적 장애일 수 있어, 짧은 재시도 창(200ms 간격) 안의 실패만으로
        // "예약이 거부했다"고 단정해 정상 결제를 환불해버리면 더 위험하다 — 재시도만 하고 끝낸다.
        PaymentRow row = pendingReservationDepositRow();
        given(paymentMapper.selectById(2L)).willReturn(row);
        given(paymentMapper.markProcessing(eq(2L), any(LocalDateTime.class))).willReturn(1);
        given(tossPaymentClient.confirmPayment(eq("paymentKey123"), eq("PAYMENT_2"), eq(30000L)))
                .willReturn(new TossPaymentResponse(
                        "paymentKey123", "PAYMENT_2", "DONE", 30000L, "카드", OffsetDateTime.now(), null, null
                ));
        given(paymentMapper.markCompleted(any(PaymentRow.class))).willReturn(1);
        willThrow(HttpServerErrorException.create(
                HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0], null))
                .given(reservationPaymentContractClient).completePayment(any(), any(), any(), any(), any());

        PaymentResponse result = paymentService.confirmPayment(2L, 90L, new ConfirmPaymentRequest("paymentKey123"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        verify(reservationPaymentContractClient, times(3)).completePayment(any(), any(), any(), any(), any());
        verify(refundService, never()).refund(any(), any(), any());
    }

    @Test
    @DisplayName("행사ID로 결제 목록을 조회하면 필터 조건이 매퍼에 그대로 전달되고 페이지 응답으로 감싸진다")
    void getPayments_필터조회_페이지응답반환() {
        // Arrange
        PaymentRow row = new PaymentRow();
        row.setPaymentId(1L);
        row.setPaymentType("VENDOR_FEE");
        row.setAmount(50000L);
        row.setStatus("COMPLETED");
        row.setFairId(10L);
        given(paymentMapper.selectByFilter(eq(10L), isNull(), isNull(), isNull(), isNull(), eq(0L), eq(20)))
                .willReturn(List.of(row));
        given(paymentMapper.countByFilter(eq(10L), isNull(), isNull(), isNull(), isNull())).willReturn(1L);

        // Act
        PaymentListResponse result = paymentService.getPayments(10L, null, null, null, 0, 20);

        // Assert: content 변환(PaymentRow -> PaymentResponse)과 페이지 메타(공통 목록 응답 포맷)가
        // 둘 다 맞는지
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).paymentId()).isEqualTo(1L);
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(20);
        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.totalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("조건에 맞는 결제가 없으면 빈 목록을 반환한다")
    void getPayments_결과없음_빈목록반환() {
        given(paymentMapper.selectByFilter(any(), any(), any(), any(), any(), anyLong(), anyInt()))
                .willReturn(List.of());
        given(paymentMapper.countByFilter(any(), any(), any(), any(), any())).willReturn(0L);

        PaymentListResponse result = paymentService.getPayments(null, null, null, null, 0, 20);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
        assertThat(result.totalPages()).isEqualTo(0);
    }

    @Test
    @DisplayName("내 결제내역을 조회하면 로그인 사용자 기준으로만 필터링된다")
    void getMyPayments_본인결제내역만조회() {
        given(paymentMapper.selectByFilter(isNull(), isNull(), isNull(), isNull(), eq(90L), eq(0L), eq(20)))
                .willReturn(List.of());
        given(paymentMapper.countByFilter(isNull(), isNull(), isNull(), isNull(), eq(90L))).willReturn(0L);

        PaymentListResponse result = paymentService.getMyPayments(90L, 0, 20);

        assertThat(result.content()).isEmpty();
        // fairId·businessId·paymentType·status는 걸지 않고 payerUserId만 거는지(마이페이지 = 본인 것만)
        verify(paymentMapper).selectByFilter(isNull(), isNull(), isNull(), isNull(), eq(90L), eq(0L), eq(20));
    }

    // page/size 검증은 컨트롤러의 @Min/@Max가 아니라 여기(서비스 계층)에서 한다 —
    // standaloneSetup 기반 컨트롤러 테스트에서 메서드 파라미터 검증이 실제로 안 걸리는 걸
    // 확인해서(CodeRabbit 리뷰 지적, PR #62) 프레임워크 동작에 기대지 않기로 했다. page가
    // 음수면 SQL의 OFFSET이 음수가 돼서 DB 에러로 이어질 수 있어 특히 중요한 방어다.
    @Test
    @DisplayName("페이지 번호가 음수면 예외를 던진다")
    void getPayments_페이지음수_예외를던진다() {
        assertThatThrownBy(() -> paymentService.getPayments(null, null, null, null, -1, 20))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(paymentMapper, never()).selectByFilter(any(), any(), any(), any(), any(), anyLong(), anyInt());
    }

    @Test
    @DisplayName("페이지 크기가 0 이하이거나 100을 초과하면 예외를 던진다")
    void getPayments_페이지크기범위밖_예외를던진다() {
        assertThatThrownBy(() -> paymentService.getPayments(null, null, null, null, 0, 0))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        assertThatThrownBy(() -> paymentService.getPayments(null, null, null, null, 0, 101))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    @DisplayName("내 결제내역도 페이지 범위를 벗어나면 예외를 던진다")
    void getMyPayments_페이지범위밖_예외를던진다() {
        assertThatThrownBy(() -> paymentService.getMyPayments(90L, -1, 20))
                .isInstanceOf(CommonException.class)
                .extracting(e -> ((CommonException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    // WAITING_FOR_DEPOSIT 상태의 결제 하나를 미리 만들어두는 헬퍼.
    // handleDepositWebhook 테스트들이 전부 이 상황에서 시작한다.
    //
    // 결제유형이 예약금인데, 예약금은 이제 가상계좌를 지원하지 않는다
    // (PaymentService.rejectVirtualAccountForReservationDeposit). 즉 정상 경로에서는 더 이상
    // 만들어지지 않는 조합이지만, 이미 DB에 남아있는 과거 데이터와 우회 시도 잔여물이 이 상태로
    // 존재할 수 있어 웹훅 처리 자체는 계속 동작해야 한다 - 그래서 픽스처를 그대로 둔다.
    private PaymentRow waitingForDepositRow() {
        PaymentRow row = new PaymentRow();
        row.setPaymentId(1L);
        row.setOrderId("PAYMENT_1");
        row.setPaymentType("RESERVATION_DEPOSIT");
        row.setAmount(30000L);
        row.setStatus("WAITING_FOR_DEPOSIT");
        row.setMethod("가상계좌");
        row.setPayerUserId(90L);
        row.setFairId(10L);
        row.setReservationId(500L);
        row.setVirtualAccountBankCode("020");
        row.setVirtualAccountNumber("1234567890");
        row.setVirtualAccountSecret("secret-abc");
        return row;
    }

    private TossWebhookEvent depositCallback(String status, String secret) {
        return new TossWebhookEvent("DEPOSIT_CALLBACK", new TossWebhookEvent.Data("PAYMENT_1", status, secret));
    }

    @Test
    @DisplayName("승인 단계에서 거절한 예약금 가상계좌에 뒤늦게 입금완료 웹훅이 와도 완료로 바꾸지 않는다")
    void handleDepositWebhook_거절된예약금결제는_완료로바꾸지않는다() {
        // 우회로 계좌가 발급됐다가 승인 거절로 FAILED가 된 건에 실제 입금이 들어온 상황.
        // 여기서 결제를 완료로 바꿔버리면 예약은 없는데 결제만 완료된 상태가 된다 - 상태는 그대로
        // 두고(멱등 무시) 운영자가 볼 수 있게 log.error만 남긴다.
        PaymentRow row = waitingForDepositRow();
        row.setStatus("FAILED");
        given(paymentMapper.selectByOrderId("PAYMENT_1")).willReturn(row);

        paymentService.handleDepositWebhook(depositCallback("DONE", "secret-abc"));

        verify(paymentMapper, never()).markVirtualAccountCompleted(
                anyLong(), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(reservationPaymentContractClient, never())
                .completePayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("입금완료 웹훅을 받으면 WAITING_FOR_DEPOSIT 결제가 COMPLETED로 전환된다")
    void handleDepositWebhook_입금완료_COMPLETED로전환() {
        PaymentRow row = waitingForDepositRow();
        given(paymentMapper.selectByOrderId("PAYMENT_1")).willReturn(row);
        given(paymentMapper.markVirtualAccountCompleted(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(1);

        paymentService.handleDepositWebhook(depositCallback("DONE", "secret-abc"));

        verify(paymentMapper).markVirtualAccountCompleted(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class));
        // 예약금 결제 완료 후속처리(도메인 통지 등)까지 이어지는지 — confirmPayment의
        // completePaymentAndNotify를 그대로 공유하는지 확인.
        verify(reservationPaymentContractClient).completePayment(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("입금취소 웹훅을 받으면 WAITING_FOR_DEPOSIT 결제가 FAILED로 전환된다")
    void handleDepositWebhook_입금취소_FAILED로전환() {
        PaymentRow row = waitingForDepositRow();
        given(paymentMapper.selectByOrderId("PAYMENT_1")).willReturn(row);
        given(paymentMapper.markVirtualAccountDepositFailed(eq(1L), any(LocalDateTime.class))).willReturn(1);

        paymentService.handleDepositWebhook(depositCallback("CANCELED", "secret-abc"));

        verify(paymentMapper).markVirtualAccountDepositFailed(eq(1L), any(LocalDateTime.class));
        verify(paymentMapper, never()).markVirtualAccountCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("secret이 일치하지 않으면 위조로 보고 무시한다")
    void handleDepositWebhook_secret불일치_무시() {
        given(paymentMapper.selectByOrderId("PAYMENT_1")).willReturn(waitingForDepositRow());

        paymentService.handleDepositWebhook(depositCallback("DONE", "wrong-secret"));

        verify(paymentMapper, never()).markVirtualAccountCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("저장된 secret이 없으면 웹훅 body에도 secret이 없어도(null==null) 통과시키지 않는다")
    void handleDepositWebhook_저장된secret없음_웹훅도secret없음_통과안됨() {
        // Objects.equals(null, null)이 true라서 저장된 secret이 비어있으면(예: 저장 버그로
        // 빠졌거나) 검증 자체가 무의미하게 통과해버리던 보안 버그의 회귀 테스트
        // (2026-08-18 CodeRabbit 리뷰 지적).
        PaymentRow row = waitingForDepositRow();
        row.setVirtualAccountSecret(null);
        given(paymentMapper.selectByOrderId("PAYMENT_1")).willReturn(row);

        paymentService.handleDepositWebhook(depositCallback("DONE", null));

        verify(paymentMapper, never()).markVirtualAccountCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("웹훅 body에 secret이 빈 문자열로 오면 통과시키지 않는다")
    void handleDepositWebhook_웹훅secret빈문자열_통과안됨() {
        given(paymentMapper.selectByOrderId("PAYMENT_1")).willReturn(waitingForDepositRow());

        paymentService.handleDepositWebhook(depositCallback("DONE", ""));

        verify(paymentMapper, never()).markVirtualAccountCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 주문번호는 무시한다")
    void handleDepositWebhook_대상없음_무시() {
        given(paymentMapper.selectByOrderId("PAYMENT_999")).willReturn(null);

        paymentService.handleDepositWebhook(
                new TossWebhookEvent("DEPOSIT_CALLBACK", new TossWebhookEvent.Data("PAYMENT_999", "DONE", "secret")));

        verify(paymentMapper, never()).markVirtualAccountCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("이미 처리된(WAITING_FOR_DEPOSIT이 아닌) 결제는 재전송된 웹훅이 와도 무시한다")
    void handleDepositWebhook_이미처리됨_무시() {
        PaymentRow row = waitingForDepositRow();
        row.setStatus("COMPLETED");
        given(paymentMapper.selectByOrderId("PAYMENT_1")).willReturn(row);

        paymentService.handleDepositWebhook(depositCallback("DONE", "secret-abc"));

        verify(paymentMapper, never()).markVirtualAccountCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("DEPOSIT_CALLBACK이 아닌 다른 eventType은 무시한다")
    void handleDepositWebhook_다른이벤트타입_무시() {
        paymentService.handleDepositWebhook(
                new TossWebhookEvent("PAYMENT_STATUS_CHANGED", new TossWebhookEvent.Data("PAYMENT_1", "DONE", "secret")));

        verify(paymentMapper, never()).selectByOrderId(any());
    }

}


