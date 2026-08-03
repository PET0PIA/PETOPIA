package com.ms.petopia.api.payment.controller;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                        1L, "VENDOR_FEE", 50000L, "COMPLETED", "MOCK",
                        LocalDateTime.of(2026, 8, 3, 10, 0),
                        LocalDateTime.of(2026, 8, 3, 10, 0),
                        10L, 20L, null, null, 40L
                )
        );

        // Act + Assert: 실제 HTTP GET 요청처럼 "/api/payments/1"을 호출하고
        // jsonPath("$.필드명")로 응답 JSON 안의 값을 하나씩 꺼내서 검증함.
        // ($는 JSON 최상위를 가리키는 표기법 — jQuery 셀렉터 비슷한 느낌)
        mockMvc.perform(get("/api/payments/1"))
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
    void returns404WhenPaymentNotFound() throws Exception {
        // Arrange: 서비스가 예외를 던지는 상황을 흉내냄
        // (PaymentServiceTest에서 이미 검증한 "존재하지 않으면 예외" 로직을,
        // 여기서는 "그 예외가 HTTP 응답으로 잘 변환되는지"만 다시 확인하는 것)
        willThrow(new CommonException(ErrorCode.PAYMENT_NOT_FOUND))
                .given(paymentService).getPayment(999L);

        mockMvc.perform(get("/api/payments/999"))
                .andExpect(status().isNotFound()) // ErrorCode.PAYMENT_NOT_FOUND가 HttpStatus.NOT_FOUND라서 404 기대
                .andExpect(jsonPath("$.code").value("P001")); // ErrorResponse.code는 ErrorCode의 "P001" 문자열
    }

    @Test
    void createsVendorFeePayment() throws Exception {
        // Arrange
        given(paymentService.payVendorFee(eq(40L), any(VendorFeePaymentRequest.class))).willReturn(
                new PaymentResponse(
                        1L, "VENDOR_FEE", 50000L, "COMPLETED", "MOCK",
                        LocalDateTime.of(2026, 8, 3, 10, 0),
                        LocalDateTime.of(2026, 8, 3, 10, 0),
                        10L, 20L, null, null, 40L
                )
        );

        // POST는 body(JSON 문자열)를 같이 보내야 함.
        // 여기 JSON 키(fairId/businessId/amount)는 VendorFeePaymentRequest 필드명과
        // 정확히 일치해야 Jackson이 자동으로 객체로 바꿔줌(대소문자도 그대로 맞춰야 함).
        mockMvc.perform(post("/api/vendor-applications/40/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fairId\":10,\"businessId\":20,\"amount\":50000}"))
                .andExpect(status().isCreated()) // 컨트롤러가 201로 응답하는지
                .andExpect(jsonPath("$.paymentType").value("VENDOR_FEE"))
                .andExpect(jsonPath("$.applicationId").value(40));

        verify(paymentService).payVendorFee(eq(40L), any(VendorFeePaymentRequest.class));
    }

    @Test
    void returns409WhenVendorFeeAlreadyPaid() throws Exception {
        // Arrange: PaymentServiceTest의 "중복결제 예외" 케이스가 컨트롤러까지
        // 올라왔을 때 409로 잘 변환되는지 확인
        willThrow(new CommonException(ErrorCode.PAYMENT_TARGET_NOT_PAYABLE))
                .given(paymentService).payVendorFee(eq(40L), any(VendorFeePaymentRequest.class));

        mockMvc.perform(post("/api/vendor-applications/40/payment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fairId\":10,\"businessId\":20,\"amount\":50000}"))
                .andExpect(status().isConflict()) // ErrorCode.PAYMENT_TARGET_NOT_PAYABLE이 HttpStatus.CONFLICT라서 409
                .andExpect(jsonPath("$.code").value("P002"));
    }

}
