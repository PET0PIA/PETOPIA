package com.ms.petopia.api.payment.controller;

import com.ms.petopia.api.payment.dto.ConfirmPaymentRequest;
import com.ms.petopia.api.payment.dto.PaymentListResponse;
import com.ms.petopia.api.payment.dto.PaymentResponse;
import com.ms.petopia.api.payment.dto.VendorFeePaymentRequest;
import com.ms.petopia.api.payment.service.PaymentService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    // 진짜 서블릿 컨테이너(톰캣)를 안 띄우고, 컨트롤러 객체 하나만 놓고
    // 가짜 HTTP 요청을 흉내내는 도구. @SpringBootTest보다 훨씬 빠름.
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // standaloneSetup: 테스트하고 싶은 컨트롤러만 콕 집어서 등록.
        // (전체 스프링 컨텍스트를 안 띄우니까 다른 빈들 신경 안 써도 됨)
        mockMvc = MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                // 실제 운영에서도 예외를 GlobalExceptionHandler가 잡아서 ErrorResponse로
                // 바꿔주니까, 테스트에서도 똑같이 등록해줘야 "예외 던지면 404/409로
                // 변환되는지"까지 검증할 수 있음. 안 넣으면 그냥 500 에러로 터짐.
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getsPaymentDetailById() throws Exception {
        // Arrange: paymentService.getPayment(1L)을 호출하면 이 응답을 리턴하도록
        // 가짜로 세팅. 실제 DB는 전혀 관여 안 함.
        given(paymentService.getPayment(1L)).willReturn(

                new PaymentResponse(
                        1L, "PAYMENT_1", "VENDOR_FEE", 50000L, "COMPLETED", "TOSS",
                        LocalDateTime.of(2026, 8, 3, 10, 0),
                        LocalDateTime.of(2026, 8, 3, 10, 0),
                        10L, 20L, null, null, 40L
                )
        );

        // Act + Assert: 실제 HTTP GET 요청처럼 "/api/payments/1"을 호출하고
        // jsonPath("$.필드명")로 응답 JSON 안의 값을 하나씩 꺼내서 검증함.
        // ($는 JSON 최상위를 가리키는 표기법 — jQuery 셀렉터 비슷한 느낌)
        mockMvc.perform(get("/api/payments/1")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99))
                .andExpect(status().isOk()) // HTTP 200인지
                .andExpect(jsonPath("$.paymentId").value(1))
                .andExpect(jsonPath("$.paymentType").value("VENDOR_FEE"))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // 컨트롤러가 진짜로 서비스의 getPayment(1L)을 호출했는지도 확인
        // (URL의 {paymentId}가 제대로 파싱돼서 넘어갔는지 검증하는 셈)
        verify(paymentService).getPayment(1L);
    }

    @Test
    void getsPaymentByReservationId() throws Exception {
        given(paymentService.getByReservationId(500L)).willReturn(
                new PaymentResponse(
                        2L, "PAYMENT_2", "RESERVATION_DEPOSIT", 30000L, "COMPLETED", "TOSS",
                        LocalDateTime.of(2026, 8, 5, 10, 0),
                        LocalDateTime.of(2026, 8, 5, 10, 0),
                        10L, null, 90L, 500L, null
                )
        );

        mockMvc.perform(get("/api/reservations/500/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(2))
                .andExpect(jsonPath("$.reservationId").value(500));
    }

    @Test
    void returns404WhenNoPaymentForReservation() throws Exception {
        willThrow(new CommonException(ErrorCode.PAYMENT_NOT_FOUND))
                .given(paymentService).getByReservationId(999L);

        mockMvc.perform(get("/api/reservations/999/payment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    void returns404WhenPaymentNotFound() throws Exception {
        // Arrange: 서비스가 예외를 던지는 상황을 흉내냄
        // (PaymentServiceTest에서 이미 검증한 "존재하지 않으면 예외" 로직을,
        // 여기서는 "그 예외가 HTTP 응답으로 잘 변환되는지"만 다시 확인하는 것)
        willThrow(new CommonException(ErrorCode.PAYMENT_NOT_FOUND))
                .given(paymentService).getPayment(999L);

        mockMvc.perform(get("/api/payments/999")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99))
                .andExpect(status().isNotFound()) // ErrorCode.PAYMENT_NOT_FOUND가 HttpStatus.NOT_FOUND라서 404 기대
                .andExpect(jsonPath("$.code").value("P001")); // ErrorResponse.code는 ErrorCode의 "P001" 문자열
    }

    @Test
    void createsVendorFeePayment() throws Exception {
        // Arrange
        given(paymentService.payVendorFee(eq(40L),eq(99L),any(VendorFeePaymentRequest.class))).willReturn(
                new PaymentResponse(
                        1L, "PAYMENT_1", "VENDOR_FEE", 50000L, "PENDING", "TOSS",
                        LocalDateTime.of(2026, 8, 3, 10, 0),
                        LocalDateTime.of(2026, 8, 3, 10, 0),
                        10L, 20L, null, null, 40L
                )
        );

        // POST는 body(JSON 문자열)를 같이 보내야 함.
        // 여기 JSON 키(fairId/businessId/amount)는 VendorFeePaymentRequest 필드명과
        // 정확히 일치해야 Jackson이 자동으로 객체로 바꿔줌(대소문자도 그대로 맞춰야 함).
        mockMvc.perform(post("/api/vendor-applications/40/payment")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fairId\":10,\"businessId\":20,\"amount\":50000}"))
                .andExpect(status().isCreated()) // 컨트롤러가 201로 응답하는지
                .andExpect(jsonPath("$.paymentType").value("VENDOR_FEE"))
                .andExpect(jsonPath("$.applicationId").value(40));

        verify(paymentService).payVendorFee(eq(40L), eq(99L), any(VendorFeePaymentRequest.class));
    }

    @Test
    void returns409WhenVendorFeeAlreadyPaid() throws Exception {
        // Arrange: PaymentServiceTest의 "중복결제 예외" 케이스가 컨트롤러까지
        // 올라왔을 때 409로 잘 변환되는지 확인
        willThrow(new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE))
                .given(paymentService).payVendorFee(eq(40L), eq(99L), any(VendorFeePaymentRequest.class));

        mockMvc.perform(post("/api/vendor-applications/40/payment")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fairId\":10,\"businessId\":20,\"amount\":50000}"))
                .andExpect(status().isConflict()) // ErrorCode.PAYMENT_TARGET_NOT_PAYABLE이 HttpStatus.CONFLICT라서 409
                .andExpect(jsonPath("$.code").value("P002"));
    }

    @Test
    void confirmsPayment() throws Exception {
        given(paymentService.confirmPayment(eq(1L), eq(99L), any(ConfirmPaymentRequest.class))).willReturn(
                new PaymentResponse(
                        1L, "PAYMENT_1", "VENDOR_FEE", 50000L, "COMPLETED", "카드",
                        LocalDateTime.of(2026, 8, 4, 10, 0),
                        LocalDateTime.of(2026, 8, 3, 10, 0),
                        10L, 20L, 99L, null, 40L
                )
        );

        mockMvc.perform(post("/api/payments/1/confirm")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"5EnNZRJGvxNa2mzq\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.method").value("카드"));

        verify(paymentService).confirmPayment(eq(1L), eq(99L), any(ConfirmPaymentRequest.class));
    }

    @Test
    void returns403WhenConfirmingSomeoneElsesPayment() throws Exception {
        // 다른 사람의 결제를 승인하려는 상황(IDOR 방지 확인)
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(paymentService).confirmPayment(eq(1L), eq(99L), any(ConfirmPaymentRequest.class));

        mockMvc.perform(post("/api/payments/1/confirm")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"5EnNZRJGvxNa2mzq\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    void returns400WhenPaymentKeyIsBlank() throws Exception {
        // @NotBlank 검증 — 서비스까지 안 가고 컨트롤러 바인딩 단계에서 걸러져야 함
        mockMvc.perform(post("/api/payments/1/confirm")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"paymentKey\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createsReservationDepositPayment() throws Exception {
        // Arrange: 예약금 결제는 요청 바디가 없음(금액을 클라이언트가 안 보냄 —
        // 서비스가 예약 도메인 컨텍스트로 진짜 금액을 받아온다는 걸 컨트롤러 테스트에서는
        // 그냥 신뢰하고, 여기선 "path variable/header가 잘 넘어가서 201로 응답하는지"만 본다).
        given(paymentService.payReservationDeposit(eq(500L), eq(99L))).willReturn(
                new PaymentResponse(
                        2L, "PAYMENT_2", "RESERVATION_DEPOSIT", 30000L, "PENDING", "TOSS",
                        null,
                        LocalDateTime.of(2026, 8, 5, 10, 0),
                        10L, null, 99L, 500L, null
                )
        );

        mockMvc.perform(post("/api/reservations/500/payment")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99))
                .andExpect(status().isCreated()) // 컨트롤러가 201로 응답하는지
                .andExpect(jsonPath("$.paymentType").value("RESERVATION_DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(30000))
                .andExpect(jsonPath("$.reservationId").value(500));

        verify(paymentService).payReservationDeposit(500L, 99L);
    }

    @Test
    void returns403WhenPayingSomeoneElsesReservationDeposit() throws Exception {
        // 다른 사람 소유의 예약에 예약금 결제를 시도하는 상황(IDOR 방지 확인)
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(paymentService).payReservationDeposit(eq(500L), eq(99L));

        mockMvc.perform(post("/api/reservations/500/payment")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    void returns409WhenReservationDepositAlreadyPaid() throws Exception {
        // 이미 결제된 예약에 다시 예약금 결제를 시도하는 상황(idempotencyKey 중복)
        willThrow(new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE))
                .given(paymentService).payReservationDeposit(eq(500L), eq(99L));

        mockMvc.perform(post("/api/reservations/500/payment")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 99))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P002"));
    }

    @Test
    void createsFairOpeningFeePayment() throws Exception {
        given(paymentService.payFairOpeningFee(eq(10L), eq(3L))).willReturn(
                new PaymentResponse(
                        3L, "PAYMENT_3", "FAIR_OPENING_FEE", 500000L, "PENDING", "TOSS",
                        null,
                        LocalDateTime.of(2026, 8, 6, 10, 0),
                        10L, null, 3L, null, null
                )
        );

        mockMvc.perform(post("/api/fairs/10/opening-payment")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentType").value("FAIR_OPENING_FEE"))
                .andExpect(jsonPath("$.fairId").value(10));

        verify(paymentService).payFairOpeningFee(eq(10L), eq(3L));
    }

    @Test
    void returns409WhenFairOpeningFeeAlreadyPaid() throws Exception {
        willThrow(new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE))
                .given(paymentService).payFairOpeningFee(eq(10L), eq(3L));

        mockMvc.perform(post("/api/fairs/10/opening-payment")
                        .header(PaymentTemporaryAuthHeaders.USER_ID, 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("P002"));
    }

    @Test
    void getsPaymentListFilteredByFair() throws Exception {
        given(paymentService.getPayments(eq(10L), isNull(), isNull(), isNull(), eq(0), eq(20))).willReturn(
                new PaymentListResponse(List.of(
                        new PaymentResponse(
                                1L, "PAYMENT_1", "VENDOR_FEE", 50000L, "COMPLETED", "카드",
                                LocalDateTime.of(2026, 8, 3, 10, 0),
                                LocalDateTime.of(2026, 8, 3, 10, 0),
                                10L, 20L, 99L, null, 40L
                        )
                ), 0, 20, 1L, 1)
        );

        mockMvc.perform(get("/api/payments").param("fairId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));

        verify(paymentService).getPayments(eq(10L), isNull(), isNull(), isNull(), eq(0), eq(20));
    }

    @Test
    void returns400WhenPageIsNegative() throws Exception {
        // page/size 검증은 서비스 계층 책임(PaymentServiceTest 참고) — 여기선 그 예외가
        // 컨트롤러까지 올라왔을 때 400으로 잘 변환되는지만 확인
        willThrow(new CommonException(ErrorCode.INVALID_INPUT_VALUE))
                .given(paymentService).getPayments(any(), any(), any(), any(), eq(-1), anyInt());

        mockMvc.perform(get("/api/payments").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void returns400WhenSizeIsZero() throws Exception {
        willThrow(new CommonException(ErrorCode.INVALID_INPUT_VALUE))
                .given(paymentService).getPayments(any(), any(), any(), any(), anyInt(), eq(0));

        mockMvc.perform(get("/api/payments").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void returns400WhenSizeExceedsMax() throws Exception {
        willThrow(new CommonException(ErrorCode.INVALID_INPUT_VALUE))
                .given(paymentService).getPayments(any(), any(), any(), any(), anyInt(), eq(101));

        mockMvc.perform(get("/api/payments").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("C001"));
    }

    @Test
    void getsEmptyPaymentListWhenNoFilterMatches() throws Exception {
        given(paymentService.getPayments(isNull(), isNull(), isNull(), isNull(), eq(0), eq(20)))
                .willReturn(new PaymentListResponse(List.of(), 0, 20, 0L, 0));

        mockMvc.perform(get("/api/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    void getsMyPaymentList() throws Exception {
        given(paymentService.getMyPayments(eq(99L), eq(0), eq(20)))
                .willReturn(new PaymentListResponse(List.of(
                        new PaymentResponse(
                                1L, "PAYMENT_1", "VENDOR_FEE", 50000L, "COMPLETED", "카드",
                                LocalDateTime.of(2026, 8, 3, 10, 0),
                                LocalDateTime.of(2026, 8, 3, 10, 0),
                                10L, 20L, 99L, null, 40L
                        )
                ), 0, 20, 1L, 1));

        mockMvc.perform(get("/api/me/payments").header(PaymentTemporaryAuthHeaders.USER_ID, 99))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].payerUserId").value(99));

        verify(paymentService).getMyPayments(eq(99L), eq(0), eq(20));
    }

}
