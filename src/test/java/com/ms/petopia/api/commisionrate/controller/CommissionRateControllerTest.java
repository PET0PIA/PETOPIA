package com.ms.petopia.api.commisionrate.controller;

import com.ms.petopia.api.commisionrate.dto.CommissionRateResponse;
import com.ms.petopia.api.commisionrate.service.CommissionRateService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/*
 * CommissionRateController 통합 테스트. @AuthenticationPrincipal은 ApplicationControllerTest와
 * 동일한 가짜 인증 필터 패턴으로 시뮬레이션한다.
 */
@ExtendWith(MockitoExtension.class)
class CommissionRateControllerTest {

    private static final String AUTHENTICATED_USER_ID_ATTRIBUTE = "authenticatedUserId";

    @Mock
    private CommissionRateService commissionRateService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new CommissionRateController(commissionRateService))
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
    void getsGlobalRateWithoutFairId() throws Exception {
        given(commissionRateService.getEffectiveRate(isNull())).willReturn(
                new CommissionRateResponse("GLOBAL", null, new java.math.BigDecimal("0.0500"), 99L,
                        LocalDateTime.of(2026, 8, 6, 10, 0))
        );

        mockMvc.perform(get("/api/settlements/commission-rate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("GLOBAL"))
                .andExpect(jsonPath("$.rate").value(0.05));
    }

    @Test
    void getsFairOverrideRateWithFairId() throws Exception {
        given(commissionRateService.getEffectiveRate(eq(10L))).willReturn(
                new CommissionRateResponse("FAIR", 10L, new java.math.BigDecimal("0.0300"), 99L,
                        LocalDateTime.of(2026, 8, 6, 10, 0))
        );

        mockMvc.perform(get("/api/settlements/commission-rate").param("fairId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("FAIR"))
                .andExpect(jsonPath("$.fairId").value(10));
    }

    @Test
    void setsGlobalRate() throws Exception {
        given(commissionRateService.setRate(any(), isNull(), any(), eq(99L))).willReturn(
                new CommissionRateResponse("GLOBAL", null, new java.math.BigDecimal("0.0600"), 99L,
                        LocalDateTime.of(2026, 8, 6, 10, 0))
        );

        mockMvc.perform(put("/api/settlements/commission-rate")
                        .with(authenticatedAs(99L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"GLOBAL\",\"rate\":0.06}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rate").value(0.06));
    }

    @Test
    void returns400WhenScopeFairMismatch() throws Exception {
        willThrow(new CommonException(ErrorCode.COMMISSION_RATE_INVALID_SCOPE))
                .given(commissionRateService).setRate(any(), isNull(), any(), eq(99L));

        mockMvc.perform(put("/api/settlements/commission-rate")
                        .with(authenticatedAs(99L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"FAIR\",\"rate\":0.06}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CR001"));
    }

    @Test
    void returns400WhenRateOutOfRange() throws Exception {
        mockMvc.perform(put("/api/settlements/commission-rate")
                        .with(authenticatedAs(99L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"GLOBAL\",\"rate\":1.5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns400WhenScopeMissing() throws Exception {
        mockMvc.perform(put("/api/settlements/commission-rate")
                        .with(authenticatedAs(99L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rate\":0.06}"))
                .andExpect(status().isBadRequest());
    }
}
