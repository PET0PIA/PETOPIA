package com.ms.petopia.api.refund.controller;

import com.ms.petopia.api.refund.dto.RefundResponse;
import com.ms.petopia.api.refund.dto.RefundRow;
import com.ms.petopia.api.refund.service.RefundService;
import com.ms.petopia.global.exception.CommonException;
import com.ms.petopia.global.exception.ErrorCode;
import com.ms.petopia.global.exception.GlobalExceptionHandler;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * RefundController 통합 테스트. @AuthenticationPrincipal은 ApplicationControllerTest와
 * 동일한 가짜 인증 필터 패턴으로 시뮬레이션한다. refundService.assertRequesterAuthorized는
 * Mock이라 기본적으로 아무것도 안 하므로(void 메서드 기본 no-op) 거부 케이스만 willThrow로 스텁한다.
 */
@ExtendWith(MockitoExtension.class)
class RefundControllerTest {

    private static final String AUTHENTICATED_USER_ID_ATTRIBUTE = "authenticatedUserId";

    @Mock
    private RefundService refundService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new RefundController(refundService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .addFilters(new TestAuthenticationFilter())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RequestPostProcessor authenticatedAs(Long userId) {
        return request -> {
            request.setAttribute(AUTHENTICATED_USER_ID_ATTRIBUTE, userId);
            return request;
        };
    }

    private static final class TestAuthenticationFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
        ) throws ServletException, IOException {
            Long userId = (Long) request.getAttribute(AUTHENTICATED_USER_ID_ATTRIBUTE);
            if (userId != null) {
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(userId, null, List.of()));
            }
            try {
                filterChain.doFilter(request, response);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }

    @Test
    void createsRefund() throws Exception {
        given(refundService.refund(eq(1L), eq(99L), any())).willReturn(
                new RefundResponse(
                        1L, 1L, 500L, "USER_CANCEL", "RESERVATION", 50000L, "COMPLETED",
                        LocalDateTime.of(2026, 8, 5, 10, 0),
                        LocalDateTime.of(2026, 8, 5, 10, 0)
                )
        );

        mockMvc.perform(post("/api/payments/1/refunds")
                        .with(authenticatedAs(99L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundReason\":\"USER_CANCEL\",\"requestedByDomain\":\"RESERVATION\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.refundAmount").value(50000));
    }

    @Test
    void returns403WhenRequestingSomeoneElsesRefund() throws Exception {
        // 결제 소유자도, 그 행사 담당 관리자도 아닌 사용자가 환불을 요청하는 상황(IDOR 방지 확인)
        willThrow(new CommonException(ErrorCode.ACCESS_DENIED))
                .given(refundService).assertRequesterAuthorized(1L, 77L);

        mockMvc.perform(post("/api/payments/1/refunds")
                        .with(authenticatedAs(77L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundReason\":\"USER_CANCEL\",\"requestedByDomain\":\"RESERVATION\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("A002"));
    }

    @Test
    void returns404WhenRefundingNonExistentPayment() throws Exception {
        willThrow(new CommonException(ErrorCode.PAYMENT_NOT_FOUND))
                .given(refundService).refund(eq(999L), eq(99L), any());

        mockMvc.perform(post("/api/payments/999/refunds")
                        .with(authenticatedAs(99L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundReason\":\"USER_CANCEL\",\"requestedByDomain\":\"RESERVATION\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("P001"));
    }

    @Test
    void returns409WhenPaymentAlreadyRefunded() throws Exception {
        willThrow(new CommonException(ErrorCode.REFUND_ALREADY_PROCESSED))
                .given(refundService).refund(eq(1L), eq(99L), any());

        mockMvc.perform(post("/api/payments/1/refunds")
                        .with(authenticatedAs(99L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundReason\":\"USER_CANCEL\",\"requestedByDomain\":\"RESERVATION\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RF003"));
    }

    @Test
    void returns400WhenRefundReasonIsBlank() throws Exception {
        mockMvc.perform(post("/api/payments/1/refunds")
                        .with(authenticatedAs(99L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refundReason\":\"\",\"requestedByDomain\":\"RESERVATION\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getsRefundsByPayment_환불있으면단일항목리스트() throws Exception {
        RefundRow row = new RefundRow();
        row.setRefundId(1L);
        row.setPaymentId(1L);
        row.setRefundReason("USER_CANCEL");
        row.setRequestedByDomain("RESERVATION");
        row.setRefundAmount(50000L);
        row.setStatus("COMPLETED");
        row.setRequestedAt(LocalDateTime.of(2026, 8, 5, 10, 0));
        row.setProcessedAt(LocalDateTime.of(2026, 8, 5, 10, 0));
        given(refundService.findByPaymentId(1L)).willReturn(row);

        mockMvc.perform(get("/api/payments/1/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].refundId").value(1));
    }

    @Test
    void getsRefundsByPayment_환불없으면빈리스트() throws Exception {
        given(refundService.findByPaymentId(2L)).willReturn(null);

        mockMvc.perform(get("/api/payments/2/refunds"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getsRefundDetailById() throws Exception {
        given(refundService.getRefund(1L)).willReturn(
                new RefundResponse(
                        1L, 1L, 500L, "USER_CANCEL", "RESERVATION", 50000L, "COMPLETED",
                        LocalDateTime.of(2026, 8, 5, 10, 0),
                        LocalDateTime.of(2026, 8, 5, 10, 0)
                )
        );

        mockMvc.perform(get("/api/refunds/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.refundId").value(1));
    }

    @Test
    void returns404WhenRefundNotFound() throws Exception {
        willThrow(new CommonException(ErrorCode.REFUND_NOT_FOUND))
                .given(refundService).getRefund(999L);

        mockMvc.perform(get("/api/refunds/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RF001"));
    }
}
